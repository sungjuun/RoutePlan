package com.routeplan.integration.retry;

import com.routeplan.integration.google.ExternalProviderFailure;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ExternalRetryMetrics {

    private final MeterRegistry meterRegistry;

    public ExternalRetryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAttempt(
            ExternalApiOperation operation,
            String outcome,
            ExternalProviderFailure failure
    ) {
        Counter.builder("routeplan.external.api.attempts")
                .description("External API HTTP attempts")
                .tag("provider", operation.provider())
                .tag("operation", operation.operation())
                .tag("outcome", outcome)
                .tag("reason", failure == null ? "none" : failure.name())
                .register(meterRegistry)
                .increment();
    }

    public void recordRetry(
            ExternalApiOperation operation,
            ExternalProviderFailure failure
    ) {
        counter("routeplan.external.api.retries", "External API retry decisions", operation, failure)
                .increment();
    }

    public void recordExhausted(
            ExternalApiOperation operation,
            ExternalProviderFailure failure
    ) {
        counter("routeplan.external.api.exhausted", "Exhausted external API retries", operation, failure)
                .increment();
    }

    private Counter counter(
            String name,
            String description,
            ExternalApiOperation operation,
            ExternalProviderFailure failure
    ) {
        return Counter.builder(name)
                .description(description)
                .tag("provider", operation.provider())
                .tag("operation", operation.operation())
                .tag("reason", failure.name())
                .register(meterRegistry);
    }
}
