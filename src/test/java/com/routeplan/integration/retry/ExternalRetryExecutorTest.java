package com.routeplan.integration.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExternalRetryExecutorTest {

    @Test
    void retriesTransientFailuresWithCappedExponentialBackoff() {
        ExternalRetryProperties properties = properties(4, 100, 250);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        List<Duration> delays = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        ExternalRetryExecutor executor = executor(properties, registry, delays);

        assertThatThrownBy(() -> executor.execute(
                ExternalApiOperation.GOOGLE_PLACES,
                () -> {
                    attempts.incrementAndGet();
                    throw failure(ExternalProviderFailure.RATE_LIMITED);
                }
        )).isInstanceOf(ExternalProviderException.class);

        assertThat(attempts).hasValue(4);
        assertThat(delays).containsExactly(
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(250)
        );
        assertThat(counter(registry, "routeplan.external.api.attempts", "outcome", "failure"))
                .isEqualTo(4.0);
        assertThat(counter(registry, "routeplan.external.api.retries", "reason", "RATE_LIMITED"))
                .isEqualTo(3.0);
        assertThat(counter(registry, "routeplan.external.api.exhausted", "reason", "RATE_LIMITED"))
                .isEqualTo(1.0);
    }

    @Test
    void returnsWhenARepeatedRequestEventuallySucceeds() {
        ExternalRetryProperties properties = properties(3, 10, 100);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        List<Duration> delays = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        ExternalRetryExecutor executor = executor(properties, registry, delays);

        String result = executor.execute(ExternalApiOperation.OPENAI_RESPONSES, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw failure(ExternalProviderFailure.UNAVAILABLE);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(Duration.ofMillis(10), Duration.ofMillis(20));
        assertThat(counter(registry, "routeplan.external.api.attempts", "outcome", "success"))
                .isEqualTo(1.0);
    }

    @Test
    void doesNotRetryPermanentFailure() {
        ExternalRetryProperties properties = properties(3, 10, 100);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        List<Duration> delays = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        ExternalRetryExecutor executor = executor(properties, registry, delays);

        assertThatThrownBy(() -> executor.execute(
                ExternalApiOperation.GOOGLE_ROUTES,
                () -> {
                    attempts.incrementAndGet();
                    throw failure(ExternalProviderFailure.INVALID_RESPONSE);
                }
        )).isInstanceOf(ExternalProviderException.class);

        assertThat(attempts).hasValue(1);
        assertThat(delays).isEmpty();
        assertThat(registry.find("routeplan.external.api.retries").counter()).isNull();
    }

    @Test
    void restoresInterruptFlagWhenBackoffIsInterrupted() {
        ExternalRetryProperties properties = properties(3, 10, 100);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExternalRetryExecutor executor = new ExternalRetryExecutor(
                properties,
                new ExternalRetryMetrics(registry),
                duration -> {
                    throw new InterruptedException("test interruption");
                },
                () -> 0.5
        );

        try {
            assertThatThrownBy(() -> executor.execute(
                    ExternalApiOperation.GOOGLE_ROUTES,
                    () -> {
                        throw failure(ExternalProviderFailure.UNAVAILABLE);
                    }
            ))
                    .isInstanceOfSatisfying(ExternalProviderException.class, exception ->
                            assertThat(exception.failure())
                                    .isEqualTo(ExternalProviderFailure.UNAVAILABLE));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private ExternalRetryProperties properties(
            int maxAttempts,
            long initialDelayMillis,
            long maxDelayMillis
    ) {
        ExternalRetryProperties properties = new ExternalRetryProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setInitialDelay(Duration.ofMillis(initialDelayMillis));
        properties.setMaxDelay(Duration.ofMillis(maxDelayMillis));
        properties.setJitter(0.0);
        return properties;
    }

    private ExternalRetryExecutor executor(
            ExternalRetryProperties properties,
            SimpleMeterRegistry registry,
            List<Duration> delays
    ) {
        return new ExternalRetryExecutor(
                properties,
                new ExternalRetryMetrics(registry),
                delays::add,
                () -> 0.5
        );
    }

    private ExternalProviderException failure(ExternalProviderFailure failure) {
        return new ExternalProviderException(failure, "test failure");
    }

    private double counter(
            SimpleMeterRegistry registry,
            String name,
            String tagName,
            String tagValue
    ) {
        return registry.get(name).tag(tagName, tagValue).counter().count();
    }
}
