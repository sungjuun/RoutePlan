package com.routeplan.common.api;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String path,
        Instant timestamp,
        List<Violation> violations
) {

    public ErrorResponse {
        violations = List.copyOf(violations);
    }

    public record Violation(
            Long placeId,
            String placeName,
            String reason,
            String message
    ) {
    }
}
