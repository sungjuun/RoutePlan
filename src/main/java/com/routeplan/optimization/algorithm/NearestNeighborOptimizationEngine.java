package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NearestNeighborOptimizationEngine implements OptimizationEngine {

    private final RouteProvider routeProvider;

    public NearestNeighborOptimizationEngine(RouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    @Override
    public OptimizationAlgorithm algorithm() {
        return OptimizationAlgorithm.NEAREST_NEIGHBOR;
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request) {
        RouteCostEvaluator evaluator = new RouteCostEvaluator(routeProvider, request.transportMode());
        List<VisitCandidate> order = NearestNeighborRouteBuilder.build(
                request.startLocation(), request.candidates(), evaluator
        );
        return OptimizationResults.from(algorithm(), request.startLocation(), order, evaluator);
    }
}
