package com.routeplan.integration.retry;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;

public final class ExternalRetryTestSupport {

    private ExternalRetryTestSupport() {
    }

    public static ExternalRetryExecutor noDelayRetryExecutor(int maxAttempts) {
        ExternalRetryProperties properties = new ExternalRetryProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setInitialDelay(Duration.ZERO);
        properties.setMaxDelay(Duration.ZERO);
        properties.setJitter(0.0);
        return new ExternalRetryExecutor(
                properties,
                new ExternalRetryMetrics(new SimpleMeterRegistry())
        );
    }
}
