package com.routeplan.optimization.constraint;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduledVisit(
        long tripPlaceId,
        long placeId,
        int sequence,
        LocalDate visitDate,
        LocalTime arrivalTime,
        LocalTime startTime,
        LocalTime endTime,
        long travelDistanceMeters,
        int travelMinutes,
        int waitingMinutes,
        int stayMinutes,
        int priority,
        boolean mustVisit
) {
}
