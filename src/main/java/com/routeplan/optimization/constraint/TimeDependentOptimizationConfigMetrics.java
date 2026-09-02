package com.routeplan.optimization.constraint;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Exposes the active bounded-search limits so long-running measurements can be interpreted safely. */
@Component
public class TimeDependentOptimizationConfigMetrics {

    public TimeDependentOptimizationConfigMetrics(
            MeterRegistry registry,
            TimeDependentOptimizationProperties properties
    ) {
        Gauge.builder("routeplan.optimization.time_dependent.config.beam_width",
                        properties, TimeDependentOptimizationProperties::getBeamWidth)
                .description("Configured time-dependent beam width")
                .register(registry);
        Gauge.builder("routeplan.optimization.time_dependent.config.max_states",
                        properties, TimeDependentOptimizationProperties::getMaxEvaluatedStates)
                .description("Configured time-dependent evaluated-state limit")
                .register(registry);
        Gauge.builder("routeplan.optimization.time_dependent.config.max_duration_seconds",
                        properties, value -> value.getMaxSearchDuration().toMillis() / 1_000.0)
                .description("Configured time-dependent search duration limit")
                .register(registry);
    }
}
