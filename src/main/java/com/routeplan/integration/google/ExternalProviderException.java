package com.routeplan.integration.google;

public class ExternalProviderException extends RuntimeException {

    private final ExternalProviderFailure failure;

    public ExternalProviderException(ExternalProviderFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public ExternalProviderException(
            ExternalProviderFailure failure,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failure = failure;
    }

    public ExternalProviderFailure failure() {
        return failure;
    }
}
