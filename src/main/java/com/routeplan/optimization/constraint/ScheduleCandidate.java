package com.routeplan.optimization.constraint;

import com.routeplan.optimization.domain.Location;
import java.time.LocalTime;
import java.util.Objects;

public record ScheduleCandidate(
        long tripPlaceId,
        long placeId,
        String placeName,
        Location location,
        int priority,
        boolean mustVisit,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean closed,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        int stayMinutes
) {

    public ScheduleCandidate {
        if (tripPlaceId <= 0 || placeId <= 0) {
            throw new IllegalArgumentException("장소 식별자는 양수여야 합니다.");
        }
        if (placeName == null || placeName.isBlank()) {
            throw new IllegalArgumentException("장소 이름은 필수입니다.");
        }
        Objects.requireNonNull(location, "장소 좌표는 필수입니다.");
        if (priority < 1 || priority > 100 || stayMinutes <= 0) {
            throw new IllegalArgumentException("우선순위와 체류시간이 올바르지 않습니다.");
        }
        if (closed && (openingTime != null || closingTime != null)) {
            throw new IllegalArgumentException("휴무일에는 영업시간을 지정할 수 없습니다.");
        }
        if (!closed && ((openingTime == null) != (closingTime == null))) {
            throw new IllegalArgumentException("영업 시작·종료시간은 함께 지정해야 합니다.");
        }
    }
}
