package com.routeplan.optimization.algorithm;

import com.routeplan.itinerary.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.OptimizedStop;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NearestNeighborOptimizationEngine implements OptimizationEngine {

    private static final Comparator<EvaluatedCandidate> NEAREST_ORDER = Comparator
            .comparingInt((EvaluatedCandidate evaluated) -> evaluated.route().estimatedTravelMinutes())
            .thenComparingLong(evaluated -> evaluated.route().distanceMeters())
            .thenComparingLong(evaluated -> evaluated.candidate().tripPlaceId());

    private final RouteProvider routeProvider;

    public NearestNeighborOptimizationEngine(RouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request) {
        List<VisitCandidate> remaining = new ArrayList<>(request.candidates());
        List<OptimizedStop> stops = new ArrayList<>(remaining.size());
        Location current = request.startLocation();
        long totalDistanceMeters = 0;
        int totalTravelMinutes = 0;
        int sequence = 1;

        while (!remaining.isEmpty()) {
            Location origin = current;
            EvaluatedCandidate nearest = remaining.stream()
                    .map(candidate -> new EvaluatedCandidate(
                            candidate,
                            routeProvider.getRoute(origin, candidate.location(), request.transportMode())
                    ))
                    .min(NEAREST_ORDER)
                    .orElseThrow();

            VisitCandidate selected = nearest.candidate();
            RouteResult route = nearest.route();
            stops.add(new OptimizedStop(
                    selected.tripPlaceId(),
                    selected.placeId(),
                    sequence++,
                    route.distanceMeters(),
                    route.estimatedTravelMinutes()
            ));
            totalDistanceMeters = Math.addExact(totalDistanceMeters, route.distanceMeters());
            totalTravelMinutes = Math.addExact(totalTravelMinutes, route.estimatedTravelMinutes());
            remaining.remove(selected);
            current = selected.location();
        }

        return new OptimizationResult(
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                stops,
                totalDistanceMeters,
                totalTravelMinutes
        );
    }

    private record EvaluatedCandidate(VisitCandidate candidate, RouteResult route) {
    }
}
