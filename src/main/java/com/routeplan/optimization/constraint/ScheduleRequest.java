package com.routeplan.optimization.constraint;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.trip.domain.TransportMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

public record ScheduleRequest(
        LocalDate visitDate,
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        Location accommodation,
        TransportMode transportMode,
        OptimizationAlgorithm algorithm,
        List<ScheduleCandidate> candidates,
        List<Long> proposedTripPlaceOrder
) {

    public ScheduleRequest {
        Objects.requireNonNull(visitDate, "방문일은 필수입니다.");
        Objects.requireNonNull(dailyStartTime, "하루 시작시간은 필수입니다.");
        Objects.requireNonNull(dailyEndTime, "하루 종료시간은 필수입니다.");
        Objects.requireNonNull(accommodation, "숙소 좌표는 필수입니다.");
        Objects.requireNonNull(transportMode, "이동수단은 필수입니다.");
        Objects.requireNonNull(algorithm, "최적화 알고리즘은 필수입니다.");
        Objects.requireNonNull(candidates, "방문 후보는 필수입니다.");
        Objects.requireNonNull(proposedTripPlaceOrder, "경로 후보 순서는 필수입니다.");
        if (!dailyEndTime.isAfter(dailyStartTime)) {
            throw new IllegalArgumentException("하루 종료시간은 시작시간보다 늦어야 합니다.");
        }
        candidates = List.copyOf(candidates);
        proposedTripPlaceOrder = List.copyOf(proposedTripPlaceOrder);
    }
}
