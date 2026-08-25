package com.routeplan.optimization.constraint;

public record ExcludedVisit(
        long placeId,
        String placeName,
        int priority,
        ExclusionReason reason
) {
}
