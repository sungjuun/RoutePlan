package com.routeplan.integration.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.retry.ExternalApiOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ExternalProviderGuardTest {

    @Test
    void opensOnlyTheFailingProviderAndRecoversWithAHalfOpenProbe() {
        MutableClock clock = new MutableClock();
        ExternalProviderGuard guard = guard(clock, 2, 2);

        fail(guard.acquire(ExternalApiOperation.GOOGLE_PLACES));
        fail(guard.acquire(ExternalApiOperation.GOOGLE_ROUTES));

        assertThatThrownBy(() -> guard.acquire(ExternalApiOperation.GOOGLE_PLACE_DETAILS))
                .isInstanceOfSatisfying(ExternalProviderException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(ExternalProviderFailure.UNAVAILABLE));
        try (var openAi = guard.acquire(ExternalApiOperation.OPENAI_RESPONSES)) {
            openAi.succeeded();
        }
        assertThat(status(guard, "google").state()).isEqualTo(ExternalProviderGuard.CircuitState.OPEN);
        assertThat(status(guard, "openai").state()).isEqualTo(ExternalProviderGuard.CircuitState.CLOSED);

        clock.advance(Duration.ofSeconds(31));
        assertThat(status(guard, "google").state()).isEqualTo(ExternalProviderGuard.CircuitState.HALF_OPEN);
        try (var probe = guard.acquire(ExternalApiOperation.GOOGLE_ROUTES)) {
            probe.succeeded();
        }
        assertThat(status(guard, "google").state()).isEqualTo(ExternalProviderGuard.CircuitState.CLOSED);
        assertThat(status(guard, "google").consecutiveFailures()).isZero();
    }

    @Test
    void rejectsExcessConcurrencyWithoutAffectingAnotherProvider() {
        ExternalProviderGuard guard = guard(new MutableClock(), 5, 1);
        try (var google = guard.acquire(ExternalApiOperation.GOOGLE_PLACES)) {
            assertThatThrownBy(() -> guard.acquire(ExternalApiOperation.GOOGLE_ROUTES))
                    .isInstanceOf(ExternalProviderException.class);
            try (var openAi = guard.acquire(ExternalApiOperation.OPENAI_RESPONSES)) {
                openAi.succeeded();
            }
            google.succeeded();
        }
    }

    @Test
    void staleInFlightSuccessCannotCloseANewlyOpenedCircuit() {
        ExternalProviderGuard guard = guard(new MutableClock(), 1, 2);
        var stale = guard.acquire(ExternalApiOperation.GOOGLE_PLACES);
        fail(guard.acquire(ExternalApiOperation.GOOGLE_ROUTES));

        stale.succeeded();
        stale.close();

        assertThat(status(guard, "google").state()).isEqualTo(ExternalProviderGuard.CircuitState.OPEN);
    }

    private ExternalProviderGuard guard(Clock clock, int threshold, int maxConcurrent) {
        ExternalResilienceProperties properties = new ExternalResilienceProperties();
        properties.setFailureThreshold(threshold);
        properties.setOpenDuration(Duration.ofSeconds(30));
        properties.setMaxConcurrentCalls(maxConcurrent);
        return new ExternalProviderGuard(properties,
                new ExternalResilienceMetrics(new SimpleMeterRegistry()), clock);
    }

    private void fail(ExternalProviderGuard.Permit permit) {
        try (permit) {
            permit.failed(ExternalProviderFailure.UNAVAILABLE);
        }
    }

    private ExternalProviderGuard.ProviderStatus status(ExternalProviderGuard guard, String provider) {
        return guard.current().stream().filter(value -> value.provider().equals(provider))
                .findFirst().orElseThrow();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-08-31T00:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
