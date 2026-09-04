package com.routeplan.collaboration.application;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.optimization.domain.Location;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceOpeningHour;
import com.routeplan.place.persistence.PlaceOpeningHourRepository;
import com.routeplan.place.persistence.PlaceRepository;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NearbyPlaceRecommendationService {

    private static final double MAX_CURRENT_DISTANCE_METERS = 5_000;
    private static final double WALKING_METERS_PER_MINUTE = 80;
    private static final int MAX_CANDIDATES = 250;

    private final ResourceAccessService access;
    private final TripRepository trips;
    private final TripPlaceRepository tripPlaces;
    private final PlaceRepository places;
    private final PlaceOpeningHourRepository openingHours;
    private final JdbcTemplate jdbc;

    public NearbyPlaceRecommendationService(
            ResourceAccessService access,
            TripRepository trips,
            TripPlaceRepository tripPlaces,
            PlaceRepository places,
            PlaceOpeningHourRepository openingHours,
            JdbcTemplate jdbc
    ) {
        this.access = access;
        this.trips = trips;
        this.tripPlaces = tripPlaces;
        this.places = places;
        this.openingHours = openingHours;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<NearbyPlaceView> recommend(long tripId, long userId, NearbyQuery query) {
        access.requireTripViewer(tripId, userId);
        validate(query);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        if (query.date().isBefore(trip.getStartDate()) || query.date().isAfter(trip.getEndDate())) {
            throw new IllegalArgumentException("추천 날짜는 여행 기간 안이어야 합니다.");
        }
        Location current = Location.of(query.currentLatitude(), query.currentLongitude());
        Place nextPlace = query.nextPlaceId() == null ? null : places.findById(query.nextPlaceId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        if (nextPlace != null && !tripPlaces.existsByTripIdAndPlaceId(tripId, nextPlace.getId())) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND);
        }
        Location next = nextPlace == null
                ? current
                : Location.of(nextPlace.getLatitude(), nextPlace.getLongitude());
        Set<String> interests = interests(userId);
        List<Long> candidateIds = jdbc.query("""
                WITH origin AS (
                    SELECT ST_SetSRID(ST_MakePoint(
                        CAST(? AS double precision), CAST(? AS double precision)
                    ), 4326)::geography AS location
                )
                SELECT place.id
                FROM places place CROSS JOIN origin
                WHERE ST_DWithin(place.location, origin.location, ?)
                  AND NOT EXISTS (
                      SELECT 1 FROM trip_places trip_place
                      WHERE trip_place.trip_id=? AND trip_place.place_id=place.id
                  )
                ORDER BY place.location <-> origin.location, place.id
                LIMIT ?
                """, (rs, row) -> rs.getLong(1),
                query.currentLongitude(), query.currentLatitude(),
                MAX_CURRENT_DISTANCE_METERS, tripId, MAX_CANDIDATES);
        List<Place> candidates = places.findAllById(candidateIds);
        var hours = openingHours.findAllByPlaceIdIn(candidates.stream().map(Place::getId).toList()).stream()
                .filter(hour -> hour.getDayOfWeek() == query.date().getDayOfWeek())
                .collect(Collectors.toMap(hour -> hour.getPlace().getId(), hour -> hour));
        double directMeters = distance(current, next);
        int limit = Math.min(10, Math.max(1, query.maxResults()));
        return candidates.stream()
                .map(place -> score(place, current, next, directMeters, hours.get(place.getId()), interests, query))
                .filter(view -> view != null)
                .sorted(Comparator.comparingDouble(NearbyPlaceView::score).reversed()
                        .thenComparingLong(NearbyPlaceView::detourMeters)
                        .thenComparingLong(NearbyPlaceView::placeId))
                .limit(limit)
                .toList();
    }

    private NearbyPlaceView score(
            Place place,
            Location current,
            Location next,
            double directMeters,
            PlaceOpeningHour hours,
            Set<String> interests,
            NearbyQuery query
    ) {
        Location candidate = Location.of(place.getLatitude(), place.getLongitude());
        double fromCurrent = distance(current, candidate);
        if (fromCurrent > MAX_CURRENT_DISTANCE_METERS) {
            return null;
        }
        double toNext = distance(candidate, next);
        long travelMeters = Math.round(fromCurrent + toNext);
        long detourMeters = Math.max(0, Math.round(fromCurrent + toNext - directMeters));
        int walkingMinutes = (int) Math.ceil(travelMeters / WALKING_METERS_PER_MINUTE);
        int requiredMinutes = walkingMinutes + place.getAverageStayMinutes();
        if (requiredMinutes > query.availableMinutes()) {
            return null;
        }
        LocalTime visitStart = query.currentTime().plusMinutes((long) Math.ceil(fromCurrent / WALKING_METERS_PER_MINUTE));
        LocalTime visitEnd = visitStart.plusMinutes(place.getAverageStayMinutes());
        boolean hoursKnown = hours != null;
        boolean open = hours == null || (!hours.isClosed()
                && !visitStart.isBefore(hours.getOpenTime())
                && !visitEnd.isAfter(hours.getCloseTime()));
        if (!open) {
            return null;
        }
        boolean interestMatch = matchesInterest(place.getCategory(), interests);
        double score = 100.0
                - detourMeters / 120.0
                - walkingMinutes * 0.45
                - place.getAverageStayMinutes() * 0.05
                + (interestMatch ? 18 : 0)
                + (hoursKnown ? 4 : 0);
        return new NearbyPlaceView(
                place.getId(), place.getName(), place.getCategory(),
                place.getLatitude(), place.getLongitude(),
                Math.round(fromCurrent), Math.round(toNext), detourMeters,
                walkingMinutes, place.getAverageStayMinutes(), requiredMinutes,
                hoursKnown, interestMatch, Math.round(score * 10.0) / 10.0
        );
    }

    private Set<String> interests(long userId) {
        String categories = jdbc.query(
                "SELECT categories FROM user_preferences WHERE user_id=?",
                (rs, row) -> rs.getString(1), userId
        ).stream().findFirst().orElse("");
        Set<String> result = new HashSet<>();
        for (String category : categories.split(",")) {
            if (!category.isBlank()) result.add(category.strip().toUpperCase(Locale.ROOT));
        }
        return result;
    }

    private boolean matchesInterest(String category, Set<String> interests) {
        if (category == null || interests.isEmpty()) return false;
        String normalized = category.toUpperCase(Locale.ROOT);
        return interests.stream().anyMatch(interest -> switch (interest) {
            case "FOOD" -> normalized.contains("FOOD") || normalized.contains("RESTAURANT")
                    || normalized.contains("CAFE") || normalized.contains("BAKERY");
            case "SHOPPING" -> normalized.contains("SHOP") || normalized.contains("STORE")
                    || normalized.contains("MALL");
            case "CULTURE" -> normalized.contains("MUSEUM") || normalized.contains("HISTOR")
                    || normalized.contains("CULTUR") || normalized.contains("ART");
            case "NATURE" -> normalized.contains("PARK") || normalized.contains("NATURE")
                    || normalized.contains("GARDEN");
            case "RELAXATION" -> normalized.contains("SPA") || normalized.contains("CAFE")
                    || normalized.contains("PARK");
            case "ADVENTURE" -> normalized.contains("ACTIVITY") || normalized.contains("SPORT")
                    || normalized.contains("ADVENTURE");
            default -> normalized.contains(interest);
        });
    }

    private static double distance(Location first, Location second) {
        double lat1 = Math.toRadians(first.latitude());
        double lat2 = Math.toRadians(second.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(second.longitude() - first.longitude());
        double haversine = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private static void validate(NearbyQuery query) {
        if (query == null || query.date() == null || query.currentTime() == null
                || query.currentLatitude() == null || query.currentLongitude() == null) {
            throw new IllegalArgumentException("현재 위치·날짜·시간은 필수입니다.");
        }
        if (query.currentLatitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || query.currentLatitude().compareTo(BigDecimal.valueOf(90)) > 0
                || query.currentLongitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || query.currentLongitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("현재 위치 좌표가 올바르지 않습니다.");
        }
        if (query.availableMinutes() < 15 || query.availableMinutes() > 720) {
            throw new IllegalArgumentException("남는 시간은 15분 이상 720분 이하여야 합니다.");
        }
    }

    public record NearbyQuery(
            LocalDate date,
            LocalTime currentTime,
            BigDecimal currentLatitude,
            BigDecimal currentLongitude,
            Long nextPlaceId,
            int availableMinutes,
            int maxResults
    ) {}

    public record NearbyPlaceView(
            long placeId,
            String name,
            String category,
            BigDecimal latitude,
            BigDecimal longitude,
            long distanceFromCurrentMeters,
            long distanceToNextMeters,
            long detourMeters,
            int walkingMinutes,
            int stayMinutes,
            int requiredMinutes,
            boolean openingHoursKnown,
            boolean interestMatch,
            double score
    ) {}
}
