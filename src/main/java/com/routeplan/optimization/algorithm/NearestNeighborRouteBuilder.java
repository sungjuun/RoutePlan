package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class NearestNeighborRouteBuilder {

    private static final Comparator<EvaluatedCandidate> NEAREST_ORDER = Comparator
            .comparingInt((EvaluatedCandidate evaluated) -> evaluated.route().estimatedTravelMinutes())
            .thenComparingLong(evaluated -> evaluated.route().distanceMeters())
            .thenComparingLong(evaluated -> evaluated.candidate().tripPlaceId());

    private NearestNeighborRouteBuilder() {
    }

    static List<VisitCandidate> build(
            Location start,
            List<VisitCandidate> candidates,
            RouteCostEvaluator evaluator
    ) {
        List<VisitCandidate> remaining = new ArrayList<>(candidates);
        List<VisitCandidate> ordered = new ArrayList<>(remaining.size());
        Location current = start;

        while (!remaining.isEmpty()) {
            Location origin = current;
            EvaluatedCandidate nearest = remaining.stream()
                    .map(candidate -> new EvaluatedCandidate(
                            candidate,
                            evaluator.route(origin, candidate.location())
                    ))
                    .min(NEAREST_ORDER)
                    .orElseThrow();
            ordered.add(nearest.candidate());
            remaining.remove(nearest.candidate());
            current = nearest.candidate().location();
        }
        return List.copyOf(ordered);
    }

    private record EvaluatedCandidate(VisitCandidate candidate, RouteResult route) {
    }
}
