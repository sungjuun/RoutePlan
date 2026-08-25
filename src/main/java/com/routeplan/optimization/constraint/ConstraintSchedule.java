package com.routeplan.optimization.constraint;

import java.time.LocalTime;
import java.util.List;

public record ConstraintSchedule(
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

    public ConstraintSchedule {
        visits = List.copyOf(visits);
        exclusions = List.copyOf(exclusions);
    }
}
