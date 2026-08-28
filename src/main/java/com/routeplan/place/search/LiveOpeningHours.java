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

/** Raw provider hours are transient. Date-specific hours override regular weekly periods. */
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
        // Capture before the request: a response crossing local midnight must not shift the seven-day window.
        Instant requestedAt = Instant.now();
        return parse(client.get(ExternalApiOperation.GOOGLE_PLACE_DETAILS,
                properties.getPlacesBaseUrl().resolve("/v1/places/" + externalId + "?languageCode=ko"),
                "id,regularOpeningHours,currentOpeningHours,utcOffsetMinutes"), requestedAt);
    }

    public Applied apply(long tripId, List<ScheduleRequest> requests) {
        if (!enabled) return new Applied(requests, List.of());
        Map<Long, Hours> hours = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        Set<Long> candidates = new HashSet<>();
        requests.forEach(r -> r.candidates().forEach(c -> {
            if (c.openingTime() == null && !c.closed()) candidates.add(c.placeId());
        }));
        for (var entry : places.findAllByTripIdOrderByIdAsc(tripId)) {
            String external = entry.getPlace().getExternalPlaceId();
            if (external == null || !candidates.contains(entry.getPlace().getId())) continue;
            Hours value = fetch(external);
            hours.put(entry.getPlace().getId(), value);
            warnings.add(entry.getPlace().getName() + ": " + value.warning());
        }
        return new Applied(requests.stream().map(r -> new ScheduleRequest(r.visitDate(), r.dailyStartTime(),
                r.dailyEndTime(), r.startLocation(), r.accommodation(), r.transportMode(), r.algorithm(),
                r.candidates().stream().map(c -> {
                    Hours value = hours.get(c.placeId());
                    if (value == null || c.openingTime() != null || c.closed()) return c;
                    Window window = value.dates().getOrDefault(r.visitDate(), value.days().get(r.visitDate().getDayOfWeek()));
                    return window == null ? c : c.withOpeningWindows(window.intervals());
                }).toList(), r.proposedTripPlaceOrder())).toList(), List.copyOf(warnings));
    }

    public static Hours parse(JsonNode json) { return parse(json, Instant.now()); }

    public static Hours parse(JsonNode json, Instant requestedAt) {
        List<String> descriptions = new ArrayList<>();
        json.path("regularOpeningHours").path("weekdayDescriptions").forEach(v -> descriptions.add(v.asText()));
        Map<DayOfWeek, Window> days = new EnumMap<>(DayOfWeek.class);
        Map<LocalDate, Window> dates = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        JsonNode regular = json.path("regularOpeningHours").path("periods");
        if (regular.isArray() && !regular.isEmpty()) {
            try {
                Map<Integer, List<OpeningWindow>> raw = new HashMap<>();
                if (alwaysOpen(regular)) {
                    for (int day = 0; day < 7; day++) raw.put(day, List.of(new OpeningWindow(0, 1440)));
                } else {
                    for (var period : regular) {
                        int start = weeklyMinute(period.path("open")), end = weeklyMinute(period.path("close"));
                        if (start == end) throw new IllegalArgumentException();
                        if (end < start) end += 7 * 1440;
                        for (int day = start / 1440; day * 1440 < end; day++) {
                            add(raw, day % 7, Math.max(0, start - day * 1440), Math.min(1440, end - day * 1440));
                        }
                    }
                }
                for (int day = 0; day < 7; day++) days.put(DayOfWeek.of(day == 0 ? 7 : day), window(raw.getOrDefault(day, List.of())));
            } catch (IllegalArgumentException exception) { warnings.add("정규 영업시간 형식이 불완전하여 적용하지 않았습니다."); }
        } else warnings.add("정규 영업시간 정보가 없습니다.");

        JsonNode current = json.path("currentOpeningHours").path("periods");
        if (current.isArray() && json.path("utcOffsetMinutes").isIntegralNumber()) {
            try {
                int offset = json.path("utcOffsetMinutes").asInt();
                if (Math.abs(offset) > 14 * 60) throw new IllegalArgumentException();
                LocalDate today = requestedAt.atOffset(ZoneOffset.ofTotalSeconds(offset * 60)).toLocalDate();
                Map<LocalDate, List<OpeningWindow>> raw = new HashMap<>();
                if (alwaysOpen(current)) {
                    for (int i = 0; i < 7; i++) raw.put(today.plusDays(i), List.of(new OpeningWindow(0, 1440)));
                } else {
                    for (var period : current) {
                        LocalDateTime start = datedPoint(period.path("open")), end = datedPoint(period.path("close"));
                        if (!end.isAfter(start) || Duration.between(start, end).toDays() > 7) throw new IllegalArgumentException();
                        for (LocalDate date = today; date.isBefore(today.plusDays(7)); date = date.plusDays(1)) {
                            if (start.isBefore(date.plusDays(1).atStartOfDay()) && end.isAfter(date.atStartOfDay())) {
                                add(raw, date, start.toLocalDate().isBefore(date) ? 0 : minute(start.toLocalTime()),
                                        end.toLocalDate().isAfter(date) ? 1440 : minute(end.toLocalTime()));
                            }
                        }
                    }
                }
                for (int i = 0; i < 7; i++) dates.put(today.plusDays(i), window(raw.getOrDefault(today.plusDays(i), List.of())));
                warnings.add(today + "~" + today.plusDays(6) + "은 제공된 특별 영업시간·휴무를 우선 적용합니다.");
            } catch (IllegalArgumentException | DateTimeException exception) {
                dates.clear();
                warnings.add("특별 영업시간 형식이 불완전하여 날짜별 휴무는 확인이 필요합니다.");
            }
        }
        warnings.add("그 밖의 날짜는 정규 영업시간 기준이며 공휴일·임시 휴무를 방문 전 확인하세요.");
        return new Hours(Map.copyOf(days), Map.copyOf(dates), List.copyOf(descriptions), String.join(" ", warnings));
    }

    private static boolean alwaysOpen(JsonNode periods) {
        if (periods.size() != 1 || periods.get(0).has("close")) return false;
        JsonNode open = periods.get(0).path("open");
        return open.path("day").asInt(-1) == 0 && open.path("hour").asInt(0) == 0 && open.path("minute").asInt(0) == 0;
    }
    private static int weeklyMinute(JsonNode point) {
        int day = point.path("day").asInt(-1);
        if (day < 0 || day > 6) throw new IllegalArgumentException();
        return day * 1440 + minute(point);
    }
    private static int minute(JsonNode point) {
        int hour = point.path("hour").asInt(0), minute = point.path("minute").asInt(0);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException();
        return hour * 60 + minute;
    }
    private static int minute(LocalTime time) { return time.toSecondOfDay() / 60; }
    private static LocalDateTime datedPoint(JsonNode point) {
        JsonNode date = point.path("date");
        return LocalDate.of(date.path("year").asInt(), date.path("month").asInt(), date.path("day").asInt())
                .atStartOfDay().plusMinutes(minute(point));
    }
    private static <K> void add(Map<K, List<OpeningWindow>> target, K key, int start, int end) {
        if (end > start) target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new OpeningWindow(start, end));
    }
    private static Window window(List<OpeningWindow> intervals) {
        List<OpeningWindow> merged = new ArrayList<>();
        for (var item : intervals.stream().sorted(Comparator.comparingInt(OpeningWindow::startMinute)).toList()) {
            if (!merged.isEmpty() && item.startMinute() <= merged.getLast().endMinute()) {
                var last = merged.removeLast();
                merged.add(new OpeningWindow(last.startMinute(), Math.max(last.endMinute(), item.endMinute())));
            } else merged.add(item);
        }
        return new Window(List.copyOf(merged));
    }
    public record Window(List<OpeningWindow> intervals) {
        public Window { intervals = List.copyOf(intervals); }
        public boolean closed() { return intervals.isEmpty(); }
        public LocalTime open() { return closed() ? null : LocalTime.ofSecondOfDay(intervals.getFirst().startMinute() * 60L); }
        public LocalTime close() { return closed() ? null : intervals.getLast().endMinute() == 1440 ? LocalTime.MAX : LocalTime.ofSecondOfDay(intervals.getLast().endMinute() * 60L); }
    }
    public record Hours(Map<DayOfWeek, Window> days, Map<LocalDate, Window> dates, List<String> weekdayDescriptions, String warning) {}
    public record Applied(List<ScheduleRequest> requests, List<String> warnings) {}
}
