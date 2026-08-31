package com.routeplan.integration.resilience;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.external.resilience")
public class ExternalResilienceProperties {

    private boolean enabled = true;
    private int failureThreshold = 5;
    private Duration openDuration = Duration.ofSeconds(30);
    private int maxConcurrentCalls = 20;
    private int failureRateMinimumCalls = 5;
    private int failureRateWarningPercent = 50;

    public void validate() {
        if (failureThreshold < 1 || failureThreshold > 100) {
            throw new IllegalStateException("Circuit Breaker 실패 기준은 1~100이어야 합니다.");
        }
        if (openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalStateException("Circuit Breaker 차단 시간은 0보다 커야 합니다.");
        }
        if (maxConcurrentCalls < 1 || maxConcurrentCalls > 1_000) {
            throw new IllegalStateException("공급자별 동시 호출 수는 1~1000이어야 합니다.");
        }
        if (failureRateMinimumCalls < 1 || failureRateMinimumCalls > 10_000) {
            throw new IllegalStateException("실패율 경고 최소 표본은 1~10000이어야 합니다.");
        }
        if (failureRateWarningPercent < 1 || failureRateWarningPercent > 100) {
            throw new IllegalStateException("실패율 경고 기준은 1~100이어야 합니다.");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getFailureThreshold() { return failureThreshold; }
    public void setFailureThreshold(int value) { failureThreshold = value; }
    public Duration getOpenDuration() { return openDuration; }
    public void setOpenDuration(Duration value) { openDuration = value; }
    public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
    public void setMaxConcurrentCalls(int value) { maxConcurrentCalls = value; }
    public int getFailureRateMinimumCalls() { return failureRateMinimumCalls; }
    public void setFailureRateMinimumCalls(int value) { failureRateMinimumCalls = value; }
    public int getFailureRateWarningPercent() { return failureRateWarningPercent; }
    public void setFailureRateWarningPercent(int value) { failureRateWarningPercent = value; }
}
