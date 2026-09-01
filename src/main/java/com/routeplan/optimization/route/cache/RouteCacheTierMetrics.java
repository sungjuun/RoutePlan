package com.routeplan.optimization.route.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RouteCacheTierMetrics {

    private final MeterRegistry registry;

    public RouteCacheTierMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void read(String tier, int hits, int misses, int failures) {
        increment("routeplan.route.cache.tier.reads", "Route cache tier reads", tier, "hit", hits);
        increment("routeplan.route.cache.tier.reads", "Route cache tier reads", tier, "miss", misses);
        increment("routeplan.route.cache.tier.reads", "Route cache tier reads", tier, "failure", failures);
    }

    void write(String tier, int entries, int failures) {
        increment("routeplan.route.cache.tier.writes", "Route cache tier writes", tier, "success",
                failures == 0 ? entries : 0);
        increment("routeplan.route.cache.tier.writes", "Route cache tier writes", tier, "failure", failures);
    }

    void cleanup(String outcome, int entries) {
        increment("routeplan.route.cache.database.cleanup", "Persistent route cache cleanup", "database", outcome,
                Math.max(entries, 1));
    }

    private void increment(String name, String description, String tier, String outcome, long amount) {
        if (amount <= 0) return;
        Counter.builder(name)
                .description(description)
                .tag("tier", tier)
                .tag("outcome", outcome)
                .register(registry)
                .increment(amount);
    }
}
