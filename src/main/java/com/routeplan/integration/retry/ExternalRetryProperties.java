package com.routeplan.integration.retry;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.external.retry")
public class ExternalRetryProperties {

    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration initialDelay = Duration.ofMillis(200);
    private Duration maxDelay = Duration.ofSeconds(2);
    private double multiplier = 2.0;
    private double jitter = 0.2;

    public Duration delayAfter(int failedAttempt, double randomUnit) {
        validate();
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("실패 시도 번호는 1 이상이어야 합니다.");
        }
        if (randomUnit < 0.0 || randomUnit > 1.0) {
            throw new IllegalArgumentException("Jitter 난수는 0과 1 사이여야 합니다.");
        }
        double exponential = initialDelay.toMillis()
                * Math.pow(multiplier, failedAttempt - 1L);
        double capped = Math.min(exponential, maxDelay.toMillis());
        double jitterFactor = 1.0 + ((randomUnit * 2.0) - 1.0) * jitter;
        long delayMillis = Math.min(
                maxDelay.toMillis(),
                Math.max(0L, Math.round(capped * jitterFactor))
        );
        return Duration.ofMillis(delayMillis);
    }

    public void validate() {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalStateException("외부 API 최대 시도 횟수는 1–10이어야 합니다.");
        }
        if (initialDelay == null || initialDelay.isNegative()
                || maxDelay == null || maxDelay.isNegative()
                || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalStateException("외부 API Retry 지연시간 설정이 올바르지 않습니다.");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalStateException("외부 API Retry 배수는 1 이상이어야 합니다.");
        }
        if (!Double.isFinite(jitter) || jitter < 0.0 || jitter > 1.0) {
            throw new IllegalStateException("외부 API Retry Jitter는 0과 1 사이여야 합니다.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(Duration initialDelay) {
        this.initialDelay = initialDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(Duration maxDelay) {
        this.maxDelay = maxDelay;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getJitter() {
        return jitter;
    }

    public void setJitter(double jitter) {
        this.jitter = jitter;
    }
}
