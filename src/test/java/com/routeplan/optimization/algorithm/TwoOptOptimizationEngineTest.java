package com.routeplan.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TwoOptOptimizationEngineTest {

    @Test
    void improvesNearestNeighborRouteUntilNoBetterReversalExists() {
        MatrixRouteProvider routeProvider = ExactSearchOptimizationEngineTest.trapMatrix();
        OptimizationRequest request = ExactSearchOptimizationEngineTest.request();

        OptimizationResult nearest = new NearestNeighborOptimizationEngine(routeProvider).optimize(request);
        OptimizationResult improved = new TwoOptOptimizationEngine(routeProvider).optimize(request);

        assertThat(improved.algorithm()).isEqualTo(OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT);
        assertThat(improved.estimatedTravelMinutes())
                .isLessThan(nearest.estimatedTravelMinutes())
                .isEqualTo(5);
        assertThat(improved.stops()).extracting(stop -> stop.tripPlaceId())
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void keepsSingleCandidateRoute() {
        RouteProvider routeProvider = (origin, destination, mode) -> new RouteResult(20, 4);
        OptimizationRequest request = new OptimizationRequest(
                new Location(0, 0),
                List.of(new VisitCandidate(
                        1, 1, new Location(0, 1)
                )),
                TransportMode.WALKING
        );

        OptimizationResult result = new TwoOptOptimizationEngine(routeProvider).optimize(request);

        assertThat(result.totalDistanceMeters()).isEqualTo(20);
        assertThat(result.stops()).hasSize(1);
    }
}
