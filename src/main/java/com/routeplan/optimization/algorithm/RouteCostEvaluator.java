package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RouteCostEvaluator {

    private final RouteProvider routeProvider;
    private final TransportMode transportMode;
    private final Map<Leg, RouteResult> cache = new HashMap<>();

    RouteCostEvaluator(RouteProvider routeProvider, TransportMode transportMode) {
        this.routeProvider = routeProvider;
        this.transportMode = transportMode;
    }

    RouteResult route(Location origin, Location destination) {
        return cache.computeIfAbsent(
                new Leg(origin, destination),
                ignored -> routeProvider.getRoute(origin, destination, transportMode)
        );
    }

    EvaluatedRoute evaluate(Location start, List<VisitCandidate> orderedCandidates) {
        List<RouteResult> legs = new ArrayList<>(orderedCandidates.size());
        PathCost cost = PathCost.ZERO;
        Location current = start;
        for (VisitCandidate candidate : orderedCandidates) {
            RouteResult leg = route(current, candidate.location());
            legs.add(leg);
            cost = cost.add(leg);
            current = candidate.location();
        }
        return new EvaluatedRoute(List.copyOf(legs), cost);
    }

    record EvaluatedRoute(List<RouteResult> legs, PathCost cost) {
    }

    private record Leg(Location origin, Location destination) {
    }
}
