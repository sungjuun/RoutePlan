package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.RouteResult;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** L1 Redis, L2 PostGIS. Every tier fails open so a cache outage never discards a valid route result. */
@Primary
@Component
public class TieredRouteLegCache implements RouteLegCache {

    private final RedisRouteLegCache redis;
    private final PostgisRouteLegCache database;
    private final RouteCacheTierMetrics metrics;

    public TieredRouteLegCache(
            ObjectProvider<RedisRouteLegCache> redis,
            ObjectProvider<PostgisRouteLegCache> database,
            RouteCacheTierMetrics metrics
    ) {
        this.redis = redis.getIfAvailable();
        this.database = database.getIfAvailable();
        this.metrics = metrics;
    }

    @Override
    public boolean enabled() {
        return redis != null || database != null;
    }

    @Override
    public RouteCacheRead getAll(Set<RouteCacheKey> keys) {
        if (keys.isEmpty() || !enabled()) return RouteCacheRead.empty();
        Map<RouteCacheKey, RouteResult> hits = new LinkedHashMap<>();
        int failures = 0;
        if (redis != null) {
            RouteCacheRead l1 = redis.getAll(keys);
            hits.putAll(l1.routes());
            failures += l1.failureCount();
            metrics.read("redis", l1.routes().size(), keys.size() - l1.routes().size(), l1.failureCount());
        }
        Set<RouteCacheKey> missing = new LinkedHashSet<>(keys);
        missing.removeAll(hits.keySet());
        if (database != null && !missing.isEmpty()) {
            RouteCacheRead l2 = database.getAll(missing);
            hits.putAll(l2.routes());
            failures += l2.failureCount();
            if (redis != null && !l2.routes().isEmpty()) {
                int warmFailures = redis.putAll(l2.routes());
                failures += warmFailures;
                metrics.write("redis_warm", l2.routes().size(), warmFailures);
            }
        }
        return new RouteCacheRead(hits, failures);
    }

    @Override
    public int putAll(Map<RouteCacheKey, RouteResult> routes) {
        int failures = 0;
        if (database != null) failures += database.putAll(routes);
        if (redis != null) {
            int l1Failures = redis.putAll(routes);
            failures += l1Failures;
            metrics.write("redis", routes.size(), l1Failures);
        }
        return failures;
    }

    @Override
    public RouteCacheLease acquireRefreshLock(Set<RouteCacheKey> keys) {
        if (redis != null) {
            RouteCacheLease lease = redis.acquireRefreshLock(keys);
            if (lease.status() != RouteCacheLease.Status.BYPASS) return lease;
        }
        return database == null ? RouteCacheLease.bypass() : database.acquireRefreshLock(keys);
    }

    @Override
    public RouteCacheRead waitForRefresh(Set<RouteCacheKey> keys) {
        if (redis != null) {
            RouteCacheRead read = redis.waitForRefresh(keys);
            if (read.routes().size() == keys.size()) return read;
        }
        return getAll(keys);
    }
}
