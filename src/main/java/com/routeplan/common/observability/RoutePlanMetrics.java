package com.routeplan.common.observability;

import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.route.RouteMatrix;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RoutePlanMetrics {

    private static final String GENERATION_DURATION =
            "routeplan.itinerary.generation.duration";
    private static final String GENERATION_TOTAL =
            "routeplan.itinerary.generation.total";
    private static final String REOPTIMIZATION_TOTAL =
            "routeplan.itinerary.reoptimization.total";
    private final MeterRegistry meterRegistry;

    public RoutePlanMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startGeneration() {
        return Timer.start(meterRegistry);
    }

    public void recordGeneration(
            Timer.Sample sample,
            GenerationType type,
            OptimizationAlgorithm algorithm,
            Outcome outcome
    ) {
        sample.stop(Timer.builder(GENERATION_DURATION)
                .description("Itinerary generation duration")
                .tag("type", type.metricValue)
                .tag("algorithm", algorithm.name())
                .tag("outcome", outcome.metricValue)
                .register(meterRegistry));
        Counter.builder(GENERATION_TOTAL)
                .description("Itinerary generation attempts")
                .tag("type", type.metricValue)
                .tag("algorithm", algorithm.name())
                .tag("outcome", outcome.metricValue)
                .register(meterRegistry)
                .increment();
        if (type == GenerationType.REOPTIMIZATION) {
            Counter.builder(REOPTIMIZATION_TOTAL)
                    .description("Itinerary reoptimization attempts")
                    .tag("algorithm", algorithm.name())
                    .tag("outcome", outcome.metricValue)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordRouteMatrix(RouteMatrix matrix) {
        Timer.builder("routeplan.route.matrix.build.duration")
                .description("Route matrix build duration")
                .tag("data_type", matrix.dataType().name())
                .tag("transport_mode", matrix.transportMode().name())
                .register(meterRegistry)
                .record(Duration.ofMillis(matrix.buildMillis()));
        increment("routeplan.route.api.calls", "External route API calls", matrix.providerCallCount());
        increment("routeplan.route.cache.hits", "Route cache hits", matrix.cacheHitCount());
        increment("routeplan.route.cache.misses", "Route cache misses", matrix.cacheMissCount());
        increment("routeplan.route.cache.failures", "Route cache failures", matrix.cacheFailureCount());
    }

    public void recordRouteApiFailure(ExternalProviderFailure failure) {
        Counter.builder("routeplan.route.api.failures")
                .description("External route API failures")
                .tag("reason", failure.name())
                .register(meterRegistry)
                .increment();
    }

    private void increment(String name, String description, long amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(name)
                .description(description)
                .register(meterRegistry)
                .increment(amount);
    }

    public enum GenerationType {
        OPTIMIZATION("optimization"),
        REOPTIMIZATION("reoptimization");

        private final String metricValue;

        GenerationType(String metricValue) {
            this.metricValue = metricValue;
        }
    }

    public enum Outcome {
        SUCCESS("success"),
        FAILURE("failure");

        private final String metricValue;

        Outcome(String metricValue) {
            this.metricValue = metricValue;
        }
    }
}
