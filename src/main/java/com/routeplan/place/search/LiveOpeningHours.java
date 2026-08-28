package com.routeplan.place.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeplan.integration.google.*;
import com.routeplan.integration.retry.ExternalApiOperation;
import com.routeplan.optimization.constraint.*;
import com.routeplan.trip.persistence.TripPlaceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Google details are used transiently; raw opening hours are never persisted or cached. */
@Service
public class LiveOpeningHours {
    private final GoogleMapsHttpClient client;
    private final GoogleMapsProperties properties;
    private final TripPlaceRepository places;
    private final boolean enabled;

    public LiveOpeningHours(GoogleMapsHttpClient client, GoogleMapsProperties properties,
            TripPlaceRepository places, @Value("${routeplan.place.provider:DISABLED}") String provider) {
        this.client = client; this.properties = properties; this.places = places; this.enabled = provider.equalsIgnoreCase("GOOGLE");
    }

    public Hours fetch(String externalId) {
        if (!enabled) throw new ExternalProviderException(ExternalProviderFailure.NOT_CONFIGURED, "Google 장소 검색 설정이 필요합니다.");
        if (externalId == null || !externalId.matches("[A-Za-z0-9_-]{1,255}")) throw new IllegalArgumentException("Google 장소 ID가 필요합니다.");
        return parse(client.get(ExternalApiOperation.GOOGLE_PLACE_DETAILS,
                properties.getPlacesBaseUrl().resolve("/v1/places/" + externalId + "?languageCode=ko"),
                "id,regularOpeningHours"));
    }

    public Applied apply(long tripId, List<ScheduleRequest> requests) {
        if (!enabled) return new Applied(requests, List.of());
        Map<Long, Hours> hours = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        Set<Long> candidates = new HashSet<>();
        requests.forEach(r -> r.candidates().forEach(c -> candidates.add(c.placeId())));
        for (var entry : places.findAllByTripIdOrderByIdAsc(tripId)) {
            String external = entry.getPlace().getExternalPlaceId();
            if (external == null || !candidates.contains(entry.getPlace().getId())) continue;
            Hours value = fetch(external);
            hours.put(entry.getPlace().getId(), value);
            if (!value.warning().isBlank()) warnings.add(entry.getPlace().getName() + ": " + value.warning());
        }
        return new Applied(requests.stream().map(r -> new ScheduleRequest(r.visitDate(), r.dailyStartTime(),
                r.dailyEndTime(), r.startLocation(), r.accommodation(), r.transportMode(), r.algorithm(),
                r.candidates().stream().map(c -> {
                    Hours value = hours.get(c.placeId());
                    if (value == null || value.days().isEmpty() || c.openingTime() != null || c.closed()) return c;
                    Window w = value.days().get(r.visitDate().getDayOfWeek());
                    if (w == null) return c;
                    return new ScheduleCandidate(c.tripPlaceId(), c.placeId(), c.placeName(), c.location(),
                            c.priority(), c.mustVisit(), w.open(), w.close(), w.closed(),
                            c.preferredStartTime(), c.preferredEndTime(), c.stayMinutes(), c.weatherScoreAdjustment());
                }).toList(), r.proposedTripPlaceOrder())).toList(), List.copyOf(warnings));
    }

    public static Hours parse(JsonNode json) {
        JsonNode regular = json.path("regularOpeningHours");
        List<String> descriptions = new ArrayList<>();
        regular.path("weekdayDescriptions").forEach(value -> descriptions.add(value.asText()));
        JsonNode periods = regular.path("periods");
        if (!periods.isArray() || periods.isEmpty()) return new Hours(Map.of(), descriptions, "영업시간 정보가 없습니다. 방문 전 확인하세요.");
        Map<DayOfWeek, Window> days = new EnumMap<>(DayOfWeek.class);
        if (periods.size() == 1 && !periods.get(0).has("close")
                && periods.get(0).path("open").path("hour").asInt(-1) == 0) {
            for (var day : DayOfWeek.values()) days.put(day, new Window(LocalTime.MIN, LocalTime.MAX, false));
            return new Hours(Map.copyOf(days), descriptions, "");
        }
        for (JsonNode period : periods) {
            JsonNode open = period.path("open"), close = period.path("close");
            int day = open.path("day").asInt(-1);
            if (day < 0 || day > 6 || !period.has("close") || day != close.path("day").asInt(-1)) {
                return new Hours(Map.of(), descriptions, "자정을 넘는 영업시간은 자동 적용하지 않습니다. 수동 시간창을 설정하세요.");
            }
            DayOfWeek weekDay = DayOfWeek.of(day == 0 ? 7 : day);
            LocalTime start = LocalTime.of(open.path("hour").asInt(), open.path("minute").asInt());
            LocalTime end = LocalTime.of(close.path("hour").asInt(), close.path("minute").asInt());
            if (days.containsKey(weekDay) || !end.isAfter(start)) {
                return new Hours(Map.of(), descriptions, "분할 영업시간은 자동 적용하지 않습니다. 수동 시간창을 설정하세요.");
            }
            days.put(weekDay, new Window(start, end, false));
        }
        for (var day : DayOfWeek.values()) days.putIfAbsent(day, new Window(null, null, true));
        return new Hours(Map.copyOf(days), List.copyOf(descriptions), "정규 영업시간입니다. 공휴일·임시 휴무는 별도 확인하세요.");
    }
    public record Window(LocalTime open, LocalTime close, boolean closed) {}
    public record Hours(Map<DayOfWeek, Window> days, List<String> weekdayDescriptions, String warning) {}
    public record Applied(List<ScheduleRequest> requests, List<String> warnings) {}
}
