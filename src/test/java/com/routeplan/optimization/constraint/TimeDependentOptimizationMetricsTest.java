package com.routeplan.optimization.constraint;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TimeDependentOptimizationMetricsTest {

    @Test
    void publishesOutcomeDimensionsAndActiveSearchLimits() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TimeDependentOptimizationProperties properties = new TimeDependentOptimizationProperties();
        properties.setBeamWidth(128);
        properties.setMaxEvaluatedStates(50_000);
        properties.setMaxSearchDuration(Duration.ofSeconds(3));
        new TimeDependentOptimizationConfigMetrics(registry, properties);
        TimeDependentOptimizationMetrics metrics = new TimeDependentOptimizationMetrics(registry);

        metrics.record("fallback", "search_limit", 48_000, 7, 2_500);

        assertThat(registry.get("routeplan.optimization.time_dependent.states")
                .tags("outcome", "fallback", "reason", "search_limit")
                .summary().totalAmount()).isEqualTo(48_000);
        assertThat(registry.get("routeplan.optimization.time_dependent.duration")
                .tags("outcome", "fallback", "reason", "search_limit")
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(2_500);
        assertThat(registry.get("routeplan.optimization.time_dependent.config.beam_width")
                .gauge().value()).isEqualTo(128);
        assertThat(registry.get("routeplan.optimization.time_dependent.config.max_states")
                .gauge().value()).isEqualTo(50_000);
        assertThat(registry.get("routeplan.optimization.time_dependent.config.max_duration_seconds")
                .gauge().value()).isEqualTo(3);
    }
}
