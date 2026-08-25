package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.RouteResult;
import java.util.Map;

public record RouteCacheRead(
        Map<RouteCacheKey, RouteResult> routes,
        int failureCount
) {

    public RouteCacheRead {
        routes = Map.copyOf(routes);
        if (failureCount < 0) {
            throw new IllegalArgumentException("Route Cache 실패 횟수는 0 이상이어야 합니다.");
        }
    }

    public static RouteCacheRead empty() {
        return new RouteCacheRead(Map.of(), 0);
    }
}
