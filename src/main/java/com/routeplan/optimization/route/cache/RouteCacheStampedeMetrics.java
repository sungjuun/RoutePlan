package com.routeplan.optimization.route.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RouteCacheStampedeMetrics {

    private final MeterRegistry registry;

    public RouteCacheStampedeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void lock(String outcome) {
        Counter.builder("routeplan.route.cache.refresh.locks")
                .description("Distributed route cache refresh lock attempts")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    void waitCompleted(String outcome, long millis) {
        Timer.builder("routeplan.route.cache.refresh.wait")
                .description("Time spent waiting for another route cache refresher")
                .tag("outcome", outcome)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, millis)));
    }
}
