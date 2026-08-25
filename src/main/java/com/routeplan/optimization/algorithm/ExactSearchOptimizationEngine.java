package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExactSearchOptimizationEngine implements OptimizationEngine {

    public static final int MAX_CANDIDATES = 10;

    private final RouteProvider routeProvider;

    public ExactSearchOptimizationEngine(RouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    @Override
    public OptimizationAlgorithm algorithm() {
        return OptimizationAlgorithm.EXACT_SEARCH;
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request) {
        if (request.candidates().size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException(
                    "Exact Search는 장소를 최대 " + MAX_CANDIDATES + "개까지 지원합니다."
            );
        }

        List<VisitCandidate> candidates = request.candidates().stream()
                .sorted(RouteOrders.stableCandidateOrder())
                .toList();
        RouteCostEvaluator evaluator = new RouteCostEvaluator(routeProvider, request.transportMode());
        Search search = new Search(request.startLocation(), candidates, evaluator);
        List<VisitCandidate> bestOrder = search.findBestOrder();
        return OptimizationResults.from(algorithm(), request.startLocation(), bestOrder, evaluator);
    }

    private static final class Search {

        private final Location start;
        private final List<VisitCandidate> candidates;
        private final RouteCostEvaluator evaluator;
        private final boolean[] used;
        private final int[] currentPath;
        private int[] bestPath;
        private PathCost bestCost;

        private Search(
                Location start,
                List<VisitCandidate> candidates,
                RouteCostEvaluator evaluator
        ) {
            this.start = start;
            this.candidates = candidates;
            this.evaluator = evaluator;
            this.used = new boolean[candidates.size()];
            this.currentPath = new int[candidates.size()];
        }

        private List<VisitCandidate> findBestOrder() {
            visit(0, start, PathCost.ZERO);
            List<VisitCandidate> result = new ArrayList<>(bestPath.length);
            for (int index : bestPath) {
                result.add(candidates.get(index));
            }
            return List.copyOf(result);
        }

        private void visit(int depth, Location current, PathCost currentCost) {
            if (depth == candidates.size()) {
                if (bestCost == null || currentCost.compareTo(bestCost) < 0) {
                    bestCost = currentCost;
                    bestPath = Arrays.copyOf(currentPath, currentPath.length);
                }
                return;
            }

            for (int index = 0; index < candidates.size(); index++) {
                if (used[index]) {
                    continue;
                }
                VisitCandidate next = candidates.get(index);
                RouteResult leg = evaluator.route(current, next.location());
                PathCost nextCost = currentCost.add(leg);
                if (bestCost != null && nextCost.compareTo(bestCost) > 0) {
                    continue;
                }

                used[index] = true;
                currentPath[depth] = index;
                visit(depth + 1, next.location(), nextCost);
                used[index] = false;
            }
        }
    }
}
