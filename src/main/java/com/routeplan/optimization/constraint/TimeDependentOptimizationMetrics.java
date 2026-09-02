package com.routeplan.optimization.constraint;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class TimeDependentOptimizationMetrics {

    private final MeterRegistry registry;

    public TimeDependentOptimizationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void record(String outcome, String reason, int states, int buckets, long millis) {
        Counter.builder("routeplan.optimization.time_dependent.total")
                .description("Time-dependent global optimization attempts")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry)
                .increment();
        DistributionSummary.builder("routeplan.optimization.time_dependent.states")
                .description("States evaluated by the bounded global optimizer")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry)
                .record(states);
        DistributionSummary.builder("routeplan.optimization.time_dependent.buckets")
                .description("Departure-time matrices used by the global optimizer")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry)
                .record(buckets);
        Timer.builder("routeplan.optimization.time_dependent.duration")
                .description("Time-dependent global optimization duration")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, millis)));
    }
}
