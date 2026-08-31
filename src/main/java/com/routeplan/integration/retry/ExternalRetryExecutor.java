package com.routeplan.integration.retry;

import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.resilience.ExternalProviderGuard;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExternalRetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExternalRetryExecutor.class);
    private final ExternalRetryProperties properties;
    private final ExternalRetryMetrics metrics;
    private final ExternalProviderGuard providerGuard;
    private final Sleeper sleeper;
    private final DoubleSupplier random;

    @Autowired
    public ExternalRetryExecutor(
            ExternalRetryProperties properties,
            ExternalRetryMetrics metrics,
            ExternalProviderGuard providerGuard
    ) {
        this(
                properties,
                metrics,
                providerGuard,
                duration -> Thread.sleep(duration.toMillis()),
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    public ExternalRetryExecutor(
            ExternalRetryProperties properties,
            ExternalRetryMetrics metrics
    ) {
        this(
                properties,
                metrics,
                null,
                duration -> Thread.sleep(duration.toMillis()),
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    ExternalRetryExecutor(
            ExternalRetryProperties properties,
            ExternalRetryMetrics metrics,
            Sleeper sleeper,
            DoubleSupplier random
    ) {
        this(properties, metrics, null, sleeper, random);
    }

    ExternalRetryExecutor(
            ExternalRetryProperties properties,
            ExternalRetryMetrics metrics,
            ExternalProviderGuard providerGuard,
            Sleeper sleeper,
            DoubleSupplier random
    ) {
        this.properties = Objects.requireNonNull(properties, "Retry 설정은 필수입니다.");
        this.metrics = Objects.requireNonNull(metrics, "Retry 메트릭은 필수입니다.");
        this.providerGuard = providerGuard;
        this.sleeper = Objects.requireNonNull(sleeper, "Retry 대기 실행기는 필수입니다.");
        this.random = Objects.requireNonNull(random, "Retry Jitter 생성기는 필수입니다.");
    }

    public <T> T execute(ExternalApiOperation operation, Supplier<T> action) {
        Objects.requireNonNull(operation, "외부 API 작업 유형은 필수입니다.");
        Objects.requireNonNull(action, "외부 API 작업은 필수입니다.");
        properties.validate();
        int maxAttempts = properties.isEnabled() ? properties.getMaxAttempts() : 1;
        ExternalProviderGuard.Permit permit = providerGuard == null
                ? null : providerGuard.acquire(operation);

        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    T result = action.get();
                    metrics.recordAttempt(operation, "success", null);
                    if (permit != null) permit.succeeded();
                    return result;
                } catch (ExternalProviderException exception) {
                    metrics.recordAttempt(operation, "failure", exception.failure());
                    boolean retryable = retryable(exception);
                    if (!retryable || attempt == maxAttempts) {
                        if (retryable && attempt == maxAttempts && maxAttempts > 1) {
                            metrics.recordExhausted(operation, exception.failure());
                        }
                        if (permit != null) permit.failed(exception.failure());
                        throw exception;
                    }
                    Duration delay = properties.delayAfter(attempt, random.getAsDouble());
                    metrics.recordRetry(operation, exception.failure());
                    log.warn(
                            "external API retry scheduled provider={} operation={} nextAttempt={} maxAttempts={} delayMs={} reason={}",
                            operation.provider(),
                            operation.operation(),
                            attempt + 1,
                            maxAttempts,
                            delay.toMillis(),
                            exception.failure()
                    );
                    waitBeforeRetry(delay);
                }
            }
            throw new IllegalStateException("외부 API Retry 실행이 결과 없이 종료됐습니다.");
        } finally {
            if (permit != null) permit.close();
        }
    }

    private boolean retryable(ExternalProviderException exception) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        return exception.failure() == ExternalProviderFailure.RATE_LIMITED
                || exception.failure() == ExternalProviderFailure.UNAVAILABLE;
    }

    private void waitBeforeRetry(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalProviderException(
                    ExternalProviderFailure.UNAVAILABLE,
                    "외부 API 재시도 대기가 중단됐습니다.",
                    exception
            );
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
