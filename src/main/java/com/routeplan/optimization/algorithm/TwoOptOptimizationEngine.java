package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TwoOptOptimizationEngine implements OptimizationEngine {

    private final RouteProvider routeProvider;

    public TwoOptOptimizationEngine(RouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    @Override
    public OptimizationAlgorithm algorithm() {
        return OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT;
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request) {
        RouteCostEvaluator evaluator = new RouteCostEvaluator(routeProvider, request.transportMode());
        List<VisitCandidate> currentOrder = NearestNeighborRouteBuilder.build(
                request.startLocation(), request.candidates(), evaluator
        );
        PathCost currentCost = evaluator.evaluate(request.startLocation(), currentOrder).cost();

        while (currentOrder.size() >= 2) {
            List<VisitCandidate> bestOrder = currentOrder;
            PathCost bestCost = currentCost;

            for (int from = 0; from < currentOrder.size() - 1; from++) {
                for (int to = from + 1; to < currentOrder.size(); to++) {
                    List<VisitCandidate> candidateOrder = reversed(currentOrder, from, to);
                    PathCost candidateCost = evaluator
                            .evaluate(request.startLocation(), candidateOrder)
                            .cost();
                    if (isBetter(candidateCost, candidateOrder, bestCost, bestOrder)) {
                        bestOrder = candidateOrder;
                        bestCost = candidateCost;
                    }
                }
            }

            if (bestOrder == currentOrder) {
                break;
            }
            currentOrder = bestOrder;
            currentCost = bestCost;
        }

        return OptimizationResults.from(algorithm(), request.startLocation(), currentOrder, evaluator);
    }

    private boolean isBetter(
            PathCost candidateCost,
            List<VisitCandidate> candidateOrder,
            PathCost bestCost,
            List<VisitCandidate> bestOrder
    ) {
        int costComparison = candidateCost.compareTo(bestCost);
        return costComparison < 0
                || costComparison == 0
                && RouteOrders.compareLexicographically(candidateOrder, bestOrder) < 0;
    }

    private List<VisitCandidate> reversed(List<VisitCandidate> source, int from, int to) {
        List<VisitCandidate> result = new ArrayList<>(source);
        Collections.reverse(result.subList(from, to + 1));
        return List.copyOf(result);
    }
}
