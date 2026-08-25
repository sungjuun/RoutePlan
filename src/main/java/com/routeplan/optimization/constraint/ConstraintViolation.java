package com.routeplan.optimization.constraint;

public record ConstraintViolation(
        long placeId,
        String placeName,
        ExclusionReason reason,
        String message
) {
}
