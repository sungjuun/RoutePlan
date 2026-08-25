package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.util.HashMap;
import java.util.Map;

final class MatrixRouteProvider implements RouteProvider {

    private final Map<Leg, RouteResult> routes = new HashMap<>();

    MatrixRouteProvider route(Location origin, Location destination, int cost) {
        routes.put(new Leg(origin, destination), new RouteResult(cost, cost));
        return this;
    }

    @Override
    public RouteResult getRoute(Location origin, Location destination, TransportMode mode) {
        RouteResult route = routes.get(new Leg(origin, destination));
        if (route == null) {
            throw new AssertionError("Unexpected route lookup: " + origin + " -> " + destination);
        }
        return route;
    }

    private record Leg(Location origin, Location destination) {
    }
}
