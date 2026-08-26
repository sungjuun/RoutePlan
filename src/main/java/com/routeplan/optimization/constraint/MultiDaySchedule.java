package com.routeplan.optimization.constraint;

import java.time.LocalTime;
import java.util.List;

public record MultiDaySchedule(
        List<DailySchedule> days,
        List<ScheduledVisit> visits,
        List<ExcludedVisit> exclusions,
        int optimizationScore,
        int visitedPriorityScore,
        long totalDistanceMeters,
        int totalTravelMinutes,
        int totalStayMinutes,
        int totalWaitingMinutes,
        long returnTravelDistanceMeters,
        int returnTravelMinutes,
        LocalTime returnArrivalTime
) {

    public MultiDaySchedule {
        days = List.copyOf(days);
        visits = List.copyOf(visits);
        exclusions = List.copyOf(exclusions);
    }
}
