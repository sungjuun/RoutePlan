package com.routeplan.optimization.route.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.route.cache")
public class RouteCacheProperties {

    private boolean enabled;
    private String keyPrefix = "routeplan:route:v1";
    private Duration walkingTtl = Duration.ofDays(7);
    private Duration drivingTtl = Duration.ofMinutes(15);
    private Duration transitTtl = Duration.ofMinutes(5);
    private Duration refreshLockTtl = Duration.ofSeconds(15);
    private Duration refreshWait = Duration.ofSeconds(2);
    private Duration refreshPollInterval = Duration.ofMillis(100);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Route Cache key prefix는 비어 있을 수 없습니다.");
        }
        this.keyPrefix = keyPrefix.strip();
    }

    public Duration getWalkingTtl() {
        return walkingTtl;
    }

    public void setWalkingTtl(Duration walkingTtl) {
        this.walkingTtl = positive(walkingTtl, "walking");
    }

    public Duration getDrivingTtl() {
        return drivingTtl;
    }

    public void setDrivingTtl(Duration drivingTtl) {
        this.drivingTtl = positive(drivingTtl, "driving");
    }

    public Duration getTransitTtl() {
        return transitTtl;
    }

    public void setTransitTtl(Duration transitTtl) {
        this.transitTtl = positive(transitTtl, "transit");
    }

    public Duration getRefreshLockTtl() { return refreshLockTtl; }
    public void setRefreshLockTtl(Duration value) { refreshLockTtl = positive(value, "refresh lock"); }
    public Duration getRefreshWait() { return refreshWait; }
    public void setRefreshWait(Duration value) { refreshWait = positive(value, "refresh wait"); }
    public Duration getRefreshPollInterval() { return refreshPollInterval; }
    public void setRefreshPollInterval(Duration value) { refreshPollInterval = positive(value, "refresh poll"); }

    private Duration positive(Duration duration, String mode) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(mode + " Route Cache TTL은 0보다 커야 합니다.");
        }
        return duration;
    }
}
