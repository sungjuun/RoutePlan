package com.routeplan.optimization.route.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.route.cache")
public class RouteCacheProperties {

    private boolean enabled;
    private boolean persistentEnabled;
    private String keyPrefix = "routeplan:route:v1";
    private Duration walkingTtl = Duration.ofDays(7);
    private Duration drivingTtl = Duration.ofMinutes(15);
    private Duration transitTtl = Duration.ofMinutes(5);
    private Duration refreshLockTtl = Duration.ofSeconds(15);
    private Duration refreshWait = Duration.ofSeconds(2);
    private Duration refreshPollInterval = Duration.ofMillis(100);
    private Duration departureBucket = Duration.ofMinutes(15);
    private Duration cleanupInterval = Duration.ofMinutes(10);
    private int databaseBatchSize = 500;
    private int cleanupBatchSize = 5_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPersistentEnabled() {
        return persistentEnabled;
    }

    public void setPersistentEnabled(boolean persistentEnabled) {
        this.persistentEnabled = persistentEnabled;
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
    public Duration getDepartureBucket() { return departureBucket; }
    public void setDepartureBucket(Duration value) { departureBucket = positive(value, "departure bucket"); }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration value) { cleanupInterval = positive(value, "cleanup interval"); }
    public int getDatabaseBatchSize() { return databaseBatchSize; }
    public void setDatabaseBatchSize(int value) { databaseBatchSize = bounded(value, 1, 2_500, "database batch size"); }
    public int getCleanupBatchSize() { return cleanupBatchSize; }
    public void setCleanupBatchSize(int value) { cleanupBatchSize = bounded(value, 1, 100_000, "cleanup batch size"); }

    private Duration positive(Duration duration, String mode) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(mode + " Route Cache TTL은 0보다 커야 합니다.");
        }
        return duration;
    }

    private int bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + "는 " + minimum + "~" + maximum + " 범위여야 합니다.");
        }
        return value;
    }
}
