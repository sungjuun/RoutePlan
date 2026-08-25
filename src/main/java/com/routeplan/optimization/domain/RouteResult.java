package com.routeplan.optimization.domain;

public record RouteResult(long distanceMeters, int estimatedTravelMinutes) {

    public RouteResult {
        if (distanceMeters < 0) {
            throw new IllegalArgumentException("이동거리는 0 이상이어야 합니다.");
        }
        if (estimatedTravelMinutes < 0) {
            throw new IllegalArgumentException("이동시간은 0 이상이어야 합니다.");
        }
    }
}
