package com.routeplan.optimization.route;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.route",
        name = "provider",
        havingValue = "SIMPLE",
        matchIfMissing = true
)
public class SimpleRouteMatrixProvider implements RouteMatrixProvider {

    private final SimpleDistanceRouteProvider routeProvider;

    public SimpleRouteMatrixProvider(SimpleDistanceRouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    @Override
    public RouteMatrix build(List<Location> locations, TransportMode transportMode) {
        List<Location> uniqueLocations = locations.stream().distinct().toList();
        long startedAt = System.nanoTime();
        Map<RouteMatrix.Leg, RouteResult> routes = new LinkedHashMap<>();
        for (Location origin : uniqueLocations) {
            for (Location destination : uniqueLocations) {
                routes.put(
                        new RouteMatrix.Leg(origin, destination),
                        routeProvider.getRoute(origin, destination, transportMode)
                );
            }
        }
        return new RouteMatrix(
                transportMode,
                RouteDataType.STRAIGHT_LINE_ESTIMATE,
                routes,
                0,
                elapsedMillis(startedAt)
        );
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
