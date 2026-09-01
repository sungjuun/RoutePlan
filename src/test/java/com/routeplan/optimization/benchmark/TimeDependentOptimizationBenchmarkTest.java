package com.routeplan.optimization.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.optimization.constraint.ConstraintSchedulePlanner;
import com.routeplan.optimization.constraint.MultiDaySchedule;
import com.routeplan.optimization.constraint.MultiDaySchedulePlanner;
import com.routeplan.optimization.constraint.ScheduleBudget;
import com.routeplan.optimization.constraint.ScheduleCandidate;
import com.routeplan.optimization.constraint.ScheduleRequest;
import com.routeplan.optimization.constraint.TimeDependentGlobalScheduleOptimizer;
import com.routeplan.optimization.constraint.TimeDependentOptimizationMetrics;
import com.routeplan.optimization.constraint.TimeDependentOptimizationProperties;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.RouteMatrixProvider;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("time-dependent-benchmark")
class TimeDependentOptimizationBenchmarkTest {

    private static final int[] PLACE_COUNTS = {4, 6, 8};
    private static final int[] BEAM_WIDTHS = {128, 256, 512};
    private static final int RUNS = 7;
    private static final Location HOTEL = location(0);

    @Test
    void comparesCandidateCountsAndBeamWidths() {
        System.out.println("TIME_DEPENDENT_BENCHMARK,places,beam_width,median_ms,evaluated_states,score,travel_minutes");
        for (int placeCount : PLACE_COUNTS) {
            for (int beamWidth : BEAM_WIDTHS) {
                Measurement measurement = measure(placeCount, beamWidth);
                System.out.printf(Locale.ROOT, "TIME_DEPENDENT_BENCHMARK,%d,%d,%.3f,%d,%d,%d%n",
                        placeCount, beamWidth, measurement.medianMilliseconds(), measurement.evaluatedStates(),
                        measurement.score(), measurement.travelMinutes());
                assertThat(measurement.applied()).isTrue();
                assertThat(measurement.visitCount()).isEqualTo(placeCount);
                assertThat(measurement.medianMilliseconds()).isLessThan(5_000);
                assertThat(measurement.evaluatedStates()).isLessThan(250_000);
            }
        }
    }

    private Measurement measure(int placeCount, int beamWidth) {
        List<ScheduleCandidate> candidates = candidates(placeCount);
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        ScheduleRequest request = new ScheduleRequest(
                date, LocalTime.of(9, 0), LocalTime.of(20, 0), HOTEL, HOTEL,
                TransportMode.DRIVING, OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT,
                candidates, candidates.stream().map(ScheduleCandidate::tripPlaceId).toList());
        RouteProvider staticRoutes = (from, to, mode) -> route(from, to, 12);
        MultiDaySchedule initial = new MultiDaySchedulePlanner(new ConstraintSchedulePlanner(staticRoutes))
                .plan(List.of(request), staticRoutes);
        long[] durations = new long[RUNS];
        TimeDependentGlobalScheduleOptimizer.Result result = null;
        for (int run = 0; run <= RUNS; run++) {
            TimeDependentGlobalScheduleOptimizer optimizer = new TimeDependentGlobalScheduleOptimizer(
                    new DeterministicTrafficMatrixProvider(), properties(beamWidth),
                    new TimeDependentOptimizationMetrics(new SimpleMeterRegistry()));
            long started = System.nanoTime();
            result = optimizer.optimize(initial, List.of(request), ScheduleBudget.unlimited(),
                    "Asia/Seoul", RouteDataType.GOOGLE_ROUTES);
            long elapsed = System.nanoTime() - started;
            if (run > 0) durations[run - 1] = elapsed;
        }
        Arrays.sort(durations);
        MultiDaySchedule schedule = result.schedule();
        return new Measurement(
                durations[durations.length / 2] / 1_000_000.0,
                result.evaluatedStates(), schedule.optimizationScore(), schedule.totalTravelMinutes(),
                schedule.visits().size(), result.applied());
    }

    private TimeDependentOptimizationProperties properties(int beamWidth) {
        TimeDependentOptimizationProperties properties = new TimeDependentOptimizationProperties();
        properties.setEnabled(true);
        properties.setMaxCandidates(8);
        properties.setMaxDays(1);
        properties.setMaxMatrixBuilds(24);
        properties.setMaxMatrixElements(5_000);
        properties.setBeamWidth(beamWidth);
        properties.setMaxEvaluatedStates(250_000);
        properties.setMaxSearchDuration(Duration.ofSeconds(10));
        return properties;
    }

    private List<ScheduleCandidate> candidates(int count) {
        List<ScheduleCandidate> values = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            Location location = location(index);
            values.add(new ScheduleCandidate(index, index, "벤치마크 장소 " + index, location,
                    100 - index, true, null, null, false, null, null, 30));
        }
        return List.copyOf(values);
    }

    private static Location location(int index) {
        return Location.of(BigDecimal.valueOf(37.55 + index * 0.006),
                BigDecimal.valueOf(126.96 + ((index * 7) % 11) * 0.004));
    }

    private RouteResult route(Location from, Location to, int traffic) {
        if (from.equals(to)) return new RouteResult(0, 0);
        int fromIndex = index(from);
        int toIndex = index(to);
        int minutes = 4 + Math.abs(fromIndex - toIndex) * 2 + Math.floorMod(fromIndex * 3 + toIndex + traffic, 7);
        return new RouteResult(minutes * 550L, minutes);
    }

    private int index(Location location) {
        return (int) Math.round((location.latitude() - 37.55) / 0.006);
    }

    private final class DeterministicTrafficMatrixProvider implements RouteMatrixProvider {
        @Override
        public RouteMatrix build(List<Location> locations, TransportMode mode) {
            return build(locations, mode, Instant.now().plus(Duration.ofDays(2)));
        }

        @Override
        public RouteMatrix build(List<Location> locations, TransportMode mode, Instant departure) {
            int hour = departure.atZone(ZoneId.of("Asia/Seoul")).getHour();
            Map<RouteMatrix.Leg, RouteResult> routes = new LinkedHashMap<>();
            for (Location origin : locations) {
                for (Location destination : locations) {
                    routes.put(new RouteMatrix.Leg(origin, destination), route(origin, destination, hour));
                }
            }
            return new RouteMatrix(mode, RouteDataType.GOOGLE_ROUTES, routes, 0, 0);
        }
    }

    private record Measurement(
            double medianMilliseconds, int evaluatedStates, int score,
            int travelMinutes, int visitCount, boolean applied
    ) {}
}
