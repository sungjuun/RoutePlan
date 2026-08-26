package com.routeplan.integration.retry;

public enum ExternalApiOperation {
    GOOGLE_PLACES("google", "places"),
    GOOGLE_ROUTES("google", "routes"),
    OPENAI_RESPONSES("openai", "responses");

    private final String provider;
    private final String operation;

    ExternalApiOperation(String provider, String operation) {
        this.provider = provider;
        this.operation = operation;
    }

    public String provider() {
        return provider;
    }

    public String operation() {
        return operation;
    }
}
