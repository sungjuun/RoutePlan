package com.routeplan.integration.resilience;

import com.routeplan.integration.retry.ExternalApiOperation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ExternalResilienceMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeCalls = new ConcurrentHashMap<>();

    public ExternalResilienceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void registerProvider(String provider) {
        states.computeIfAbsent(provider, key -> {
            AtomicInteger value = new AtomicInteger();
            Gauge.builder("routeplan.external.circuit.state", value, AtomicInteger::get)
                    .description("External provider circuit state: 0 closed, 1 half-open, 2 open")
                    .tag("provider", key)
                    .register(registry);
            return value;
        });
        activeCalls.computeIfAbsent(provider, key -> {
            AtomicInteger value = new AtomicInteger();
            Gauge.builder("routeplan.external.bulkhead.active", value, AtomicInteger::get)
                    .description("Active external provider calls")
                    .tag("provider", key)
                    .register(registry);
            return value;
        });
    }

    void state(String provider, ExternalProviderGuard.CircuitState state) {
        registerProvider(provider);
        states.get(provider).set(switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        });
    }

    void active(String provider, int value) {
        registerProvider(provider);
        activeCalls.get(provider).set(Math.max(0, value));
    }

    void opened(ExternalApiOperation operation) {
        counter("routeplan.external.circuit.opened", "External circuit openings", operation, "threshold")
                .increment();
    }

    void rejected(ExternalApiOperation operation, String reason) {
        counter("routeplan.external.calls.rejected", "Rejected external provider calls", operation, reason)
                .increment();
    }

    private Counter counter(String name, String description, ExternalApiOperation operation, String reason) {
        return Counter.builder(name)
                .description(description)
                .tag("provider", operation.provider())
                .tag("operation", operation.operation())
                .tag("reason", reason)
                .register(registry);
    }
}
