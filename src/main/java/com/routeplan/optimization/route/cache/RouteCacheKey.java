package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.Location;
import com.routeplan.trip.domain.TransportMode;
import java.util.Objects;

public record RouteCacheKey(
        Location origin,
        Location destination,
        TransportMode transportMode
) {

    public RouteCacheKey {
        Objects.requireNonNull(origin, "Route Cache 출발 좌표는 필수입니다.");
        Objects.requireNonNull(destination, "Route Cache 도착 좌표는 필수입니다.");
        Objects.requireNonNull(transportMode, "Route Cache 이동수단은 필수입니다.");
    }
}
