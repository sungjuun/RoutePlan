package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.Location;
import com.routeplan.trip.domain.TransportMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RouteCacheKey(
        Location origin,
        Location destination,
        TransportMode transportMode,
        Instant departure
) {

    private static final Instant TIMELESS = Instant.EPOCH;

    public RouteCacheKey(Location origin, Location destination, TransportMode transportMode) {
        this(origin, destination, transportMode, null);
    }

    public RouteCacheKey {
        Objects.requireNonNull(origin, "Route Cache 출발 좌표는 필수입니다.");
        Objects.requireNonNull(destination, "Route Cache 도착 좌표는 필수입니다.");
        Objects.requireNonNull(transportMode, "Route Cache 이동수단은 필수입니다.");
    }

    public Instant departureBucket(Duration bucketSize) {
        Objects.requireNonNull(bucketSize, "Route Cache 시간 버킷은 필수입니다.");
        if (departure == null || transportMode == TransportMode.WALKING) {
            return TIMELESS;
        }
        long bucketSeconds = bucketSize.toSeconds();
        if (bucketSeconds <= 0) {
            throw new IllegalArgumentException("Route Cache 시간 버킷은 1초 이상이어야 합니다.");
        }
        return Instant.ofEpochSecond(Math.floorDiv(departure.getEpochSecond(), bucketSeconds) * bucketSeconds);
    }
}
