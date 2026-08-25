package com.routeplan.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ExactSearchOptimizationEngineTest {

    private static final Location START = new Location(0, 0);
    private static final Location PLACE_A = new Location(0, 1);
    private static final Location PLACE_B = new Location(0, 2);
    private static final Location PLACE_C = new Location(0, 3);

    @Test
    void findsGloballyOptimalOpenRouteWhenNearestNeighborIsSuboptimal() {
        MatrixRouteProvider routeProvider = trapMatrix();
        OptimizationRequest request = request();

        OptimizationResult nearest = new NearestNeighborOptimizationEngine(routeProvider).optimize(request);
        OptimizationResult exact = new ExactSearchOptimizationEngine(routeProvider).optimize(request);

        assertThat(nearest.stops()).extracting(stop -> stop.tripPlaceId())
                .containsExactly(1L, 2L, 3L);
        assertThat(nearest.estimatedTravelMinutes()).isEqualTo(102);
        assertThat(exact.algorithm()).isEqualTo(OptimizationAlgorithm.EXACT_SEARCH);
        assertThat(exact.stops()).extracting(stop -> stop.tripPlaceId())
                .containsExactly(3L, 1L, 2L);
        assertThat(exact.estimatedTravelMinutes()).isEqualTo(5);
    }

    @Test
    void rejectsMoreThanTenCandidatesBeforeSearching() {
        RouteProvider unusedProvider = (origin, destination, mode) -> new RouteResult(1, 1);
        ExactSearchOptimizationEngine engine = new ExactSearchOptimizationEngine(unusedProvider);
        List<VisitCandidate> candidates = LongStream.rangeClosed(1, 11)
                .mapToObj(id -> new VisitCandidate(id, id, new Location(0, id)))
                .toList();

        assertThatThrownBy(() -> engine.optimize(new OptimizationRequest(
                START, candidates, TransportMode.WALKING
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10개");
    }

    static MatrixRouteProvider trapMatrix() {
        return new MatrixRouteProvider()
                .route(START, PLACE_A, 1)
                .route(START, PLACE_B, 2)
                .route(START, PLACE_C, 3)
                .route(PLACE_A, PLACE_B, 1)
                .route(PLACE_A, PLACE_C, 100)
                .route(PLACE_B, PLACE_A, 1)
                .route(PLACE_B, PLACE_C, 100)
                .route(PLACE_C, PLACE_A, 1)
                .route(PLACE_C, PLACE_B, 1);
    }

    static OptimizationRequest request() {
        return new OptimizationRequest(
                START,
                List.of(
                        new VisitCandidate(1, 1, PLACE_A),
                        new VisitCandidate(2, 2, PLACE_B),
                        new VisitCandidate(3, 3, PLACE_C)
                ),
                TransportMode.WALKING
        );
    }
}
