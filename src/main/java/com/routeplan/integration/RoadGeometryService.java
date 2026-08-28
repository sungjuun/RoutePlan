package com.routeplan.integration;

import com.routeplan.common.error.*;
import com.routeplan.integration.google.*;
import com.routeplan.integration.retry.ExternalApiOperation;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.domain.Location;
import com.routeplan.trip.domain.TransportMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/** Fetches selected route legs on demand. Polylines stay in the response, never in the database. */
@Service
public class RoadGeometryService {
    private final ItineraryRepository itineraries;
    private final GoogleMapsHttpClient client;
    private final GoogleMapsProperties properties;
    private final TransactionTemplate read;
    public RoadGeometryService(ItineraryRepository itineraries, GoogleMapsHttpClient client,
            GoogleMapsProperties properties, PlatformTransactionManager manager) {
        this.itineraries = itineraries; this.client = client; this.properties = properties;
        read = new TransactionTemplate(manager); read.setReadOnly(true);
    }
    public Geometry fetch(long itineraryId, LocalDate date) {
        Input input = read.execute(s -> {
            var itinerary = itineraries.findDetailedById(itineraryId)
                    .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
            if (itinerary.getTravelModeSnapshot() == null) throw new IllegalArgumentException("이전 일정은 새 버전 계산 후 실제 경로를 조회해 주세요.");
            var visits = itinerary.getItems().stream().filter(i -> date.equals(i.getVisitDate()))
                    .filter(i -> i.getStatus() != com.routeplan.itinerary.domain.ItineraryItemStatus.COMPLETED)
                    .sorted(Comparator.comparingInt(ItineraryItem::getSequence)).toList();
            if (visits.isEmpty()) throw new IllegalArgumentException("선택한 날짜에 방문 장소가 없습니다.");
            var hotel = Location.of(itinerary.getHotelLatitudeSnapshot(), itinerary.getHotelLongitudeSnapshot());
            List<Leg> legs = new ArrayList<>();
            Location origin = hotel;
            if (date.equals(itinerary.getReoptimizationStartDate())) {
                origin = Location.of(itinerary.getReoptimizationStartLatitude(), itinerary.getReoptimizationStartLongitude());
            }
            for (var visit : visits) {
                Location destination = Location.of(visit.getPlace().getLatitude(), visit.getPlace().getLongitude());
                // Arrival includes travel but not waiting; recover the planned leg departure.
                LocalTime start = visit.getArrivalTime().minusMinutes(visit.getEstimatedTravelMinutes());
                legs.add(new Leg(origin, destination, TravelTime.departure(date, start, itinerary.getTimeZoneId())));
                origin = destination;
            }
            legs.add(new Leg(origin, hotel, TravelTime.departure(date, visits.getLast().getEndTime(), itinerary.getTimeZoneId())));
            return new Input(itinerary.getTravelModeSnapshot(), List.copyOf(legs));
        });
        if (input.mode() == TransportMode.PUBLIC_TRANSIT) {
            Instant now = Instant.now();
            if (input.legs().stream().anyMatch(leg -> leg.departure().isBefore(now.minus(Duration.ofDays(7)))
                    || leg.departure().isAfter(now.plus(Duration.ofDays(100))))) {
                throw new IllegalArgumentException("대중교통 조회는 현재 기준 과거 7일~미래 100일만 지원합니다.");
            }
        }
        List<String> polylines = new ArrayList<>();
        for (Leg leg : input.legs()) {
            if (leg.origin().equals(leg.destination())) continue;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("origin", waypoint(leg.origin())); body.put("destination", waypoint(leg.destination()));
            body.put("travelMode", switch (input.mode()) { case WALKING -> "WALK"; case DRIVING -> "DRIVE"; case PUBLIC_TRANSIT -> "TRANSIT"; });
            body.put("languageCode", "ko"); body.put("polylineQuality", "OVERVIEW");
            if (input.mode() == TransportMode.PUBLIC_TRANSIT) body.put("departureTime", leg.departure().toString());
            var json = client.post(ExternalApiOperation.GOOGLE_GEOMETRY,
                    properties.getRoutesBaseUrl().resolve("/directions/v2:computeRoutes"),
                    "routes.polyline.encodedPolyline", body);
            String encoded = json.path("routes").path(0).path("polyline").path("encodedPolyline").asText("");
            if (encoded.isBlank()) throw new ExternalProviderException(ExternalProviderFailure.ROUTE_NOT_FOUND, "이동 가능한 실제 경로가 없습니다.");
            polylines.add(encoded);
        }
        return new Geometry(date, List.copyOf(polylines), "GOOGLE_MAPS", Instant.now());
    }
    private Map<String, Object> waypoint(Location l) { return Map.of("location", Map.of("latLng", Map.of("latitude", l.latitude(), "longitude", l.longitude()))); }
    private record Leg(Location origin, Location destination, Instant departure) {}
    private record Input(TransportMode mode, List<Leg> legs) {}
    public record Geometry(LocalDate date, List<String> encodedPolylines, String provider, Instant fetchedAt) {}
}
