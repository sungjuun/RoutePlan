package com.routeplan.auth;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;

public class AuthRateLimitException extends RoutePlanException {
    private final long retryAfterSeconds;

    public AuthRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.AUTH_RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }
}
