package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.RouteResult;
import java.util.Map;
import java.util.Set;

public interface RouteLegCache {

    boolean enabled();

    RouteCacheRead getAll(Set<RouteCacheKey> keys);

    int putAll(Map<RouteCacheKey, RouteResult> routes);

    default RouteCacheLease acquireRefreshLock(Set<RouteCacheKey> keys) {
        return RouteCacheLease.bypass();
    }

    default RouteCacheRead waitForRefresh(Set<RouteCacheKey> keys) {
        return getAll(keys);
    }
}
