package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.RouteResult;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.route.cache",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisabledRouteLegCache implements RouteLegCache {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public RouteCacheRead getAll(Set<RouteCacheKey> keys) {
        return RouteCacheRead.empty();
    }

    @Override
    public int putAll(Map<RouteCacheKey, RouteResult> routes) {
        return 0;
    }
}
