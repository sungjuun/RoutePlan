package com.routeplan.integration;

import java.time.*;

public final class TravelTime {
    private TravelTime() {}

    public static Instant departure(LocalDate date, LocalTime time, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        LocalDateTime local = LocalDateTime.of(date, time);
        var offsets = zoneId.getRules().getValidOffsets(local);
        if (offsets.size() != 1) {
            throw new IllegalArgumentException("서머타임 전환으로 모호하거나 존재하지 않는 출발 시각입니다. 다른 시각을 선택해 주세요.");
        }
        return local.toInstant(offsets.getFirst());
    }
}
