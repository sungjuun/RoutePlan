package com.routeplan.integration.resilience;

import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.retry.ExternalApiOperation;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Provider-scoped circuit breaker and bulkhead. Google failures never block OpenAI, and vice versa. */
@Component
public class ExternalProviderGuard {

    private static final Logger log = LoggerFactory.getLogger(ExternalProviderGuard.class);
    private final ExternalResilienceProperties properties;
    private final ExternalResilienceMetrics metrics;
    private final Clock clock;
    private final Map<String, ProviderCircuit> circuits = new ConcurrentHashMap<>();

    @Autowired
    public ExternalProviderGuard(
            ExternalResilienceProperties properties,
            ExternalResilienceMetrics metrics
    ) {
        this(properties, metrics, Clock.systemUTC());
    }

    ExternalProviderGuard(
            ExternalResilienceProperties properties,
            ExternalResilienceMetrics metrics,
            Clock clock
    ) {
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        properties.validate();
        Arrays.stream(ExternalApiOperation.values()).map(ExternalApiOperation::provider)
                .distinct().forEach(this::circuit);
    }

    public Permit acquire(ExternalApiOperation operation) {
        if (!properties.isEnabled()) return Permit.noop();
        ProviderCircuit circuit = circuit(operation.provider());
        Admission admission = circuit.allow(clock.instant());
        if (admission == Admission.REJECTED) {
            metrics.rejected(operation, "circuit_open");
            throw unavailable("외부 공급자 " + operation.provider() + " 회로가 열려 잠시 요청을 차단합니다.");
        }
        if (!circuit.bulkhead.tryAcquire()) {
            circuit.cancel(admission);
            metrics.rejected(operation, "bulkhead_full");
            throw unavailable("외부 공급자 " + operation.provider() + " 동시 요청 한도를 초과했습니다.");
        }
        metrics.active(operation.provider(), circuit.activeCalls());
        return new Permit(this, operation, circuit, admission);
    }

    public List<ProviderStatus> current() {
        return circuits.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().snapshot(entry.getKey(), clock.instant()))
                .toList();
    }

    private ProviderCircuit circuit(String provider) {
        return circuits.computeIfAbsent(provider, key -> {
            metrics.registerProvider(key);
            return new ProviderCircuit(properties.getMaxConcurrentCalls());
        });
    }

    private void success(ExternalApiOperation operation, ProviderCircuit circuit, Admission admission) {
        circuit.success(admission);
        metrics.state(operation.provider(), circuit.snapshot(operation.provider(), clock.instant()).state());
    }

    private void failure(
            ExternalApiOperation operation,
            ProviderCircuit circuit,
            Admission admission,
            ExternalProviderFailure failure
    ) {
        boolean transientFailure = failure == ExternalProviderFailure.UNAVAILABLE
                || failure == ExternalProviderFailure.RATE_LIMITED;
        boolean opened = false;
        if (transientFailure) {
            opened = circuit.failure(admission, clock.instant(), properties.getFailureThreshold(), properties.getOpenDuration());
        } else {
            circuit.success(admission);
        }
        CircuitState state = circuit.snapshot(operation.provider(), clock.instant()).state();
        metrics.state(operation.provider(), state);
        if (opened) {
            metrics.opened(operation);
            log.error(
                    "external provider circuit opened provider={} operation={} failures={} openDurationMs={}",
                    operation.provider(), operation.operation(), circuit.consecutiveFailures,
                    properties.getOpenDuration().toMillis()
            );
        }
    }

    private void release(ExternalApiOperation operation, ProviderCircuit circuit) {
        circuit.bulkhead.release();
        metrics.active(operation.provider(), circuit.activeCalls());
    }

    private ExternalProviderException unavailable(String message) {
        return new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, message);
    }

    public enum CircuitState { CLOSED, HALF_OPEN, OPEN }
    private enum Admission { CLOSED_CALL, HALF_OPEN_PROBE, REJECTED }

    public record ProviderStatus(
            String provider,
            CircuitState state,
            int consecutiveFailures,
            Instant openUntil,
            int activeCalls,
            int maxConcurrentCalls
    ) {}

    public static final class Permit implements AutoCloseable {
        private final ExternalProviderGuard owner;
        private final ExternalApiOperation operation;
        private final ProviderCircuit circuit;
        private final Admission admission;
        private final AtomicBoolean completed = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(ExternalProviderGuard owner, ExternalApiOperation operation,
                ProviderCircuit circuit, Admission admission) {
            this.owner = owner;
            this.operation = operation;
            this.circuit = circuit;
            this.admission = admission;
        }

        private static Permit noop() { return new Permit(null, null, null, Admission.CLOSED_CALL); }

        public void succeeded() {
            if (owner != null && completed.compareAndSet(false, true)) owner.success(operation, circuit, admission);
        }

        public void failed(ExternalProviderFailure failure) {
            if (owner != null && completed.compareAndSet(false, true)) owner.failure(operation, circuit, admission, failure);
        }

        @Override
        public void close() {
            if (owner == null || !closed.compareAndSet(false, true)) return;
            if (!completed.get()) failed(ExternalProviderFailure.UNAVAILABLE);
            owner.release(operation, circuit);
        }
    }

    private final class ProviderCircuit {
        private final Semaphore bulkhead;
        private CircuitState state = CircuitState.CLOSED;
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean probeInFlight;

        private ProviderCircuit(int maxConcurrentCalls) {
            this.bulkhead = new Semaphore(maxConcurrentCalls);
        }

        private synchronized Admission allow(Instant now) {
            refresh(now);
            if (state == CircuitState.OPEN) return Admission.REJECTED;
            if (state == CircuitState.HALF_OPEN) {
                if (probeInFlight) return Admission.REJECTED;
                probeInFlight = true;
                return Admission.HALF_OPEN_PROBE;
            }
            return Admission.CLOSED_CALL;
        }

        private synchronized void cancel(Admission admission) {
            if (admission == Admission.HALF_OPEN_PROBE && state == CircuitState.HALF_OPEN) {
                probeInFlight = false;
            }
        }

        private synchronized void success(Admission admission) {
            if (admission == Admission.CLOSED_CALL && state != CircuitState.CLOSED) return;
            if (admission == Admission.HALF_OPEN_PROBE && state != CircuitState.HALF_OPEN) return;
            state = CircuitState.CLOSED;
            consecutiveFailures = 0;
            openUntil = null;
            probeInFlight = false;
        }

        private synchronized boolean failure(Admission admission, Instant now, int threshold,
                java.time.Duration duration) {
            if (admission == Admission.HALF_OPEN_PROBE && state == CircuitState.HALF_OPEN) {
                open(now, duration);
                return true;
            }
            if (admission != Admission.CLOSED_CALL || state != CircuitState.CLOSED) return false;
            consecutiveFailures++;
            if (consecutiveFailures >= threshold) {
                open(now, duration);
                return true;
            }
            return false;
        }

        private void open(Instant now, java.time.Duration duration) {
            state = CircuitState.OPEN;
            openUntil = now.plus(duration);
            probeInFlight = false;
        }

        private synchronized ProviderStatus snapshot(String provider, Instant now) {
            refresh(now);
            return new ProviderStatus(provider, state, consecutiveFailures, openUntil,
                    activeCalls(), properties.getMaxConcurrentCalls());
        }

        private void refresh(Instant now) {
            if (state == CircuitState.OPEN && openUntil != null && !now.isBefore(openUntil)) {
                state = CircuitState.HALF_OPEN;
                probeInFlight = false;
            }
        }

        private int activeCalls() {
            return properties.getMaxConcurrentCalls() - bulkhead.availablePermits();
        }
    }
}
