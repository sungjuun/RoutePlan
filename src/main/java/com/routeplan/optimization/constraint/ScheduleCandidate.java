package com.routeplan.optimization.constraint;

import com.routeplan.optimization.domain.Location;
import java.time.LocalTime;
import java.util.Objects;
import java.util.List;

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
        int stayMinutes,
        int weatherScoreAdjustment,
        List<OpeningWindow> openingWindows
) {

    public ScheduleCandidate(long tripPlaceId, long placeId, String placeName, Location location,
            int priority, boolean mustVisit, LocalTime openingTime, LocalTime closingTime, boolean closed,
            LocalTime preferredStartTime, LocalTime preferredEndTime, int stayMinutes, int weatherScoreAdjustment) {
        this(tripPlaceId, placeId, placeName, location, priority, mustVisit, openingTime, closingTime, closed,
                preferredStartTime, preferredEndTime, stayMinutes, weatherScoreAdjustment,
                closed ? List.of() : List.of(new OpeningWindow(openingTime == null ? 0 : openingTime.toSecondOfDay() / 60,
                        closingTime == null ? 1440 : closingTime.toSecondOfDay() / 60)));
    }

    public ScheduleCandidate(
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
        this(
                tripPlaceId, placeId, placeName, location, priority, mustVisit,
                openingTime, closingTime, closed, preferredStartTime, preferredEndTime,
                stayMinutes, 0
        );
    }

    public ScheduleCandidate {
        openingWindows = List.copyOf(openingWindows);
        if (closed && !openingWindows.isEmpty()) throw new IllegalArgumentException("휴무일에는 영업 구간이 없습니다.");
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
        if (weatherScoreAdjustment < -100 || weatherScoreAdjustment > 100) {
            throw new IllegalArgumentException("날씨 점수 조정값이 올바르지 않습니다.");
        }
        if (closed && (openingTime != null || closingTime != null)) {
            throw new IllegalArgumentException("휴무일에는 영업시간을 지정할 수 없습니다.");
        }
        if (!closed && ((openingTime == null) != (closingTime == null))) {
            throw new IllegalArgumentException("영업 시작·종료시간은 함께 지정해야 합니다.");
        }
    }

    public int weatherAdjustedPriority() {
        return Math.max(1, Math.min(150, priority + weatherScoreAdjustment));
    }

    public ScheduleCandidate withOpeningWindows(List<OpeningWindow> windows) {
        return new ScheduleCandidate(tripPlaceId, placeId, placeName, location, priority, mustVisit,
                null, null, windows.isEmpty(), preferredStartTime, preferredEndTime, stayMinutes,
                weatherScoreAdjustment, windows);
    }

    /** Find one continuous interval large enough for the entire stay; never bridge a break. */
    public int earliestStart(int arrival, LocalTime dayStart, LocalTime dayEnd) {
        if (closed) return -1;
        int lower = Math.max(arrival, dayStart.toSecondOfDay() / 60);
        if (preferredStartTime != null) lower = Math.max(lower, preferredStartTime.toSecondOfDay() / 60);
        int upper = dayEnd.toSecondOfDay() / 60;
        if (preferredEndTime != null) upper = Math.min(upper, preferredEndTime.toSecondOfDay() / 60);
        int best = Integer.MAX_VALUE;
        for (OpeningWindow window : openingWindows) {
            int start = Math.max(lower, window.startMinute());
            if ((long) start + stayMinutes <= Math.min(upper, window.endMinute())) best = Math.min(best, start);
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }
}
