package com.routeplan.common.error;

public class RoutePlanException extends RuntimeException {

    private final ErrorCode errorCode;

    public RoutePlanException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public RoutePlanException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
