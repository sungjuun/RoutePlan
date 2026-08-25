package com.routeplan.optimization.domain;

import com.routeplan.trip.domain.TransportMode;
import java.util.List;
import java.util.Objects;

public record OptimizationRequest(
        Location startLocation,
        List<VisitCandidate> candidates,
        TransportMode transportMode
) {

    public OptimizationRequest {
        Objects.requireNonNull(startLocation, "시작 좌표는 필수입니다.");
        Objects.requireNonNull(candidates, "후보 장소 목록은 필수입니다.");
        Objects.requireNonNull(transportMode, "이동수단은 필수입니다.");
        candidates = List.copyOf(candidates);
    }
}
