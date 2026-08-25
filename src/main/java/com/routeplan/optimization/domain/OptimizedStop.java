package com.routeplan.optimization.domain;

public record OptimizedStop(
        long tripPlaceId,
        long placeId,
        int sequence,
        long travelDistanceMeters,
        int estimatedTravelMinutes
) {

    public OptimizedStop {
        if (tripPlaceId <= 0 || placeId <= 0 || sequence <= 0) {
            throw new IllegalArgumentException("장소 식별자와 방문 순서는 양수여야 합니다.");
        }
        if (travelDistanceMeters < 0 || estimatedTravelMinutes < 0) {
            throw new IllegalArgumentException("이동비용은 0 이상이어야 합니다.");
        }
    }
}
