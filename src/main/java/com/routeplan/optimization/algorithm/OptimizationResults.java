package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.OptimizedStop;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import java.util.ArrayList;
import java.util.List;

final class OptimizationResults {

    private OptimizationResults() {
    }

    static OptimizationResult from(
            OptimizationAlgorithm algorithm,
            Location start,
            List<VisitCandidate> order,
            RouteCostEvaluator evaluator
    ) {
        RouteCostEvaluator.EvaluatedRoute evaluated = evaluator.evaluate(start, order);
        List<OptimizedStop> stops = new ArrayList<>(order.size());
        for (int index = 0; index < order.size(); index++) {
            VisitCandidate candidate = order.get(index);
            RouteResult leg = evaluated.legs().get(index);
            stops.add(new OptimizedStop(
                    candidate.tripPlaceId(),
                    candidate.placeId(),
                    index + 1,
                    leg.distanceMeters(),
                    leg.estimatedTravelMinutes()
            ));
        }
        return new OptimizationResult(
                algorithm,
                stops,
                evaluated.cost().distanceMeters(),
                evaluated.cost().travelMinutes()
        );
    }
}
