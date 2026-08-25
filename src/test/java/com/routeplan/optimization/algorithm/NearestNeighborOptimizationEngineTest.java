package com.routeplan.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NearestNeighborOptimizationEngineTest {

    private static final Location START = new Location(0, 0);
    private static final Location PLACE_A = new Location(0, 1);
    private static final Location PLACE_B = new Location(0, 2);
    private static final Location PLACE_C = new Location(0, 3);

    @Test
    void repeatedlyChoosesNearestUnvisitedCandidateAndSumsLegCosts() {
        MatrixRouteProvider routeProvider = new MatrixRouteProvider()
                .route(START, PLACE_A, 10)
                .route(START, PLACE_B, 5)
                .route(START, PLACE_C, 8)
                .route(PLACE_B, PLACE_A, 7)
                .route(PLACE_B, PLACE_C, 3)
                .route(PLACE_C, PLACE_A, 2);
        NearestNeighborOptimizationEngine engine = new NearestNeighborOptimizationEngine(routeProvider);
        List<VisitCandidate> candidates = List.of(
                new VisitCandidate(101, 1, PLACE_A),
                new VisitCandidate(102, 2, PLACE_B),
                new VisitCandidate(103, 3, PLACE_C)
        );

        OptimizationResult result = engine.optimize(
                new OptimizationRequest(START, candidates, TransportMode.WALKING)
        );

        assertThat(result.stops()).extracting(stop -> stop.placeId())
                .containsExactly(2L, 3L, 1L);
        assertThat(result.stops()).extracting(stop -> stop.sequence())
                .containsExactly(1, 2, 3);
        assertThat(result.totalDistanceMeters()).isEqualTo(10);
        assertThat(result.estimatedTravelMinutes()).isEqualTo(10);
        assertThat(candidates).hasSize(3);
    }

    @Test
    void resolvesEqualCostsByTripPlaceIdForDeterministicResult() {
        RouteProvider equalCostProvider = (origin, destination, mode) -> new RouteResult(100, 5);
        NearestNeighborOptimizationEngine engine = new NearestNeighborOptimizationEngine(equalCostProvider);

        OptimizationResult result = engine.optimize(new OptimizationRequest(
                START,
                List.of(
                        new VisitCandidate(20, 2, PLACE_B),
                        new VisitCandidate(10, 1, PLACE_A)
                ),
                TransportMode.WALKING
        ));

        assertThat(result.stops()).extracting(stop -> stop.tripPlaceId())
                .containsExactly(10L, 20L);
    }

    private static final class MatrixRouteProvider implements RouteProvider {

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
    }

    private record Leg(Location origin, Location destination) {
    }
}
