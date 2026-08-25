package com.routeplan.optimization.domain;

import java.util.Objects;

public record VisitCandidate(long tripPlaceId, long placeId, Location location) {

    public VisitCandidate {
        if (tripPlaceId <= 0 || placeId <= 0) {
            throw new IllegalArgumentException("장소 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(location, "장소 좌표는 필수입니다.");
    }
}
