package com.routeplan.optimization.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.optimization.algorithm.ExactSearchOptimizationEngine;
import com.routeplan.optimization.algorithm.NearestNeighborOptimizationEngine;
import com.routeplan.optimization.algorithm.OptimizationEngine;
import com.routeplan.optimization.algorithm.TwoOptOptimizationEngine;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.optimization.route.SimpleDistanceRouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class AlgorithmBenchmarkTest {

    private static final long DATASET_SEED = 20_260_825L;
    private static final int[] PLACE_COUNTS = {5, 8, 10, 15, 20, 30, 50};
    private static final Location START = new Location(34.6654, 135.5019);

    @Test
    void benchmarkAlgorithmsOnDeterministicDataset() {
        RouteProvider routeProvider = new SimpleDistanceRouteProvider();
        Map<OptimizationAlgorithm, OptimizationEngine> engines = new EnumMap<>(OptimizationAlgorithm.class);
        engines.put(OptimizationAlgorithm.NEAREST_NEIGHBOR,
                new NearestNeighborOptimizationEngine(routeProvider));
        engines.put(OptimizationAlgorithm.EXACT_SEARCH,
                new ExactSearchOptimizationEngine(routeProvider));
        engines.put(OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT,
                new TwoOptOptimizationEngine(routeProvider));

        List<VisitCandidate> dataset = createDataset(50);
        System.out.println("BENCHMARK_ENV,java=" + System.getProperty("java.version")
                + ",os=" + System.getProperty("os.name")
                + ",arch=" + System.getProperty("os.arch")
                + ",seed=" + DATASET_SEED);
        System.out.println("BENCHMARK_RESULT,places,algorithm,median_ms,travel_minutes,distance_meters,exact_distance_gap_percent");

        for (int placeCount : PLACE_COUNTS) {
            OptimizationRequest request = new OptimizationRequest(
                    START,
                    dataset.subList(0, placeCount),
                    TransportMode.WALKING
            );
            BenchmarkMeasurement exact = placeCount <= ExactSearchOptimizationEngine.MAX_CANDIDATES
                    ? measure(engines.get(OptimizationAlgorithm.EXACT_SEARCH), request, exactRuns(placeCount))
                    : null;
            BenchmarkMeasurement nearest = measure(
                    engines.get(OptimizationAlgorithm.NEAREST_NEIGHBOR), request, 15
            );
            BenchmarkMeasurement twoOpt = measure(
                    engines.get(OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT), request, 10
            );

            if (exact != null) {
                print(placeCount, exact, exact.result().totalDistanceMeters());
            }
            print(placeCount, nearest, exact == null ? null : exact.result().totalDistanceMeters());
            print(placeCount, twoOpt, exact == null ? null : exact.result().totalDistanceMeters());

            assertValidResult(placeCount, nearest.result());
            assertValidResult(placeCount, twoOpt.result());
            assertThat(twoOpt.result().estimatedTravelMinutes())
                    .isLessThanOrEqualTo(nearest.result().estimatedTravelMinutes());
            if (exact != null) {
                assertValidResult(placeCount, exact.result());
                assertThat(exact.result().estimatedTravelMinutes())
                        .isLessThanOrEqualTo(nearest.result().estimatedTravelMinutes());
                assertThat(exact.result().estimatedTravelMinutes())
                        .isLessThanOrEqualTo(twoOpt.result().estimatedTravelMinutes());
            }
        }
    }

    private BenchmarkMeasurement measure(
            OptimizationEngine engine,
            OptimizationRequest request,
            int measurementRuns
    ) {
        engine.optimize(request);
        long[] durations = new long[measurementRuns];
        OptimizationResult result = null;
        for (int run = 0; run < measurementRuns; run++) {
            long started = System.nanoTime();
            result = engine.optimize(request);
            durations[run] = System.nanoTime() - started;
        }
        Arrays.sort(durations);
        return new BenchmarkMeasurement(
                engine.algorithm(),
                durations[durations.length / 2] / 1_000_000.0,
                result
        );
    }

    private int exactRuns(int placeCount) {
        return placeCount < 10 ? 5 : 3;
    }

    private void print(
            int placeCount,
            BenchmarkMeasurement measurement,
            Long exactDistanceMeters
    ) {
        String distanceGap = exactDistanceMeters == null
                ? "-"
                : String.format(
                        Locale.ROOT,
                        "%.2f",
                        (measurement.result().totalDistanceMeters() - exactDistanceMeters)
                                * 100.0 / exactDistanceMeters
                );
        System.out.printf(
                Locale.ROOT,
                "BENCHMARK_RESULT,%d,%s,%.3f,%d,%d,%s%n",
                placeCount,
                measurement.algorithm(),
                measurement.medianMilliseconds(),
                measurement.result().estimatedTravelMinutes(),
                measurement.result().totalDistanceMeters(),
                distanceGap
        );
    }

    private void assertValidResult(int placeCount, OptimizationResult result) {
        assertThat(result.stops()).hasSize(placeCount);
        assertThat(result.stops()).extracting(stop -> stop.placeId()).doesNotHaveDuplicates();
    }

    private List<VisitCandidate> createDataset(int size) {
        Random random = new Random(DATASET_SEED);
        List<VisitCandidate> candidates = new ArrayList<>(size);
        for (long id = 1; id <= size; id++) {
            double latitude = 34.50 + random.nextDouble() * 0.40;
            double longitude = 135.30 + random.nextDouble() * 0.45;
            candidates.add(new VisitCandidate(id, id, new Location(latitude, longitude)));
        }
        return List.copyOf(candidates);
    }

    private record BenchmarkMeasurement(
            OptimizationAlgorithm algorithm,
            double medianMilliseconds,
            OptimizationResult result
    ) {
    }
}
