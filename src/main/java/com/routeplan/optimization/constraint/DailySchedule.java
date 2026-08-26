package com.routeplan.optimization.constraint;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DailySchedule(
        int dayNumber,
        LocalDate visitDate,
        List<ScheduledVisit> visits,
        long totalDistanceMeters,
        int totalTravelMinutes,
        int totalStayMinutes,
        int totalWaitingMinutes,
        long returnTravelDistanceMeters,
        int returnTravelMinutes,
        LocalTime returnArrivalTime
) {

    public DailySchedule {
        visits = List.copyOf(visits);
    }
}
