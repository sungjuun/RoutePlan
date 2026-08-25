package com.routeplan.optimization.domain;

import com.routeplan.itinerary.domain.OptimizationAlgorithm;
import java.util.List;
import java.util.Objects;

public record OptimizationResult(
        OptimizationAlgorithm algorithm,
        List<OptimizedStop> stops,
        long totalDistanceMeters,
        int estimatedTravelMinutes
) {

    public OptimizationResult {
        Objects.requireNonNull(algorithm, "최적화 알고리즘은 필수입니다.");
        Objects.requireNonNull(stops, "최적화 결과 장소는 필수입니다.");
        stops = List.copyOf(stops);
        if (totalDistanceMeters < 0 || estimatedTravelMinutes < 0) {
            throw new IllegalArgumentException("전체 이동비용은 0 이상이어야 합니다.");
        }
    }
}
