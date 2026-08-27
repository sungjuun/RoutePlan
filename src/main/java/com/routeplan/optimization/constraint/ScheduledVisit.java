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
        boolean mustVisit,
        int weatherScoreAdjustment
) {

    public ScheduledVisit(
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
        this(
                tripPlaceId, placeId, sequence, visitDate, arrivalTime, startTime, endTime,
                travelDistanceMeters, travelMinutes, waitingMinutes, stayMinutes, priority,
                mustVisit, 0
        );
    }
}
