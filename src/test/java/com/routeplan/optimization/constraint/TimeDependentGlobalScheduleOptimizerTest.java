package com.routeplan.optimization.constraint;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeDependentGlobalScheduleOptimizerTest {

    private final Location hotel = location(0);
    private final Location a = location(1);
    private final Location b = location(2);

    @Test
    void globallyReordersVisitsUsingHourlyTrafficMatrices() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        List<ScheduleCandidate> candidates = List.of(candidate(1, a), candidate(2, b));
        ScheduleRequest request = request(date, candidates);
        RouteProvider staticRoutes = (from, to, mode) -> route(from.equals(to) ? 0 : 10);
        MultiDaySchedule initial = new MultiDaySchedulePlanner(new ConstraintSchedulePlanner(staticRoutes))
                .plan(List.of(request), staticRoutes);
        TrafficMatrixProvider provider = new TrafficMatrixProvider();
        TimeDependentOptimizationProperties properties = properties();
        var optimizer = new TimeDependentGlobalScheduleOptimizer(
                provider, properties,
                new TimeDependentOptimizationMetrics(new SimpleMeterRegistry()));

        var result = optimizer.optimize(
                initial, List.of(request), ScheduleBudget.unlimited(),
                "Asia/Seoul", RouteDataType.GOOGLE_ROUTES);

        assertThat(result.applied()).isTrue();
        assertThat(result.schedule().visits())
                .extracting(ScheduledVisit::tripPlaceId)
                .containsExactly(2L, 1L);
        assertThat(result.providerCalls()).isPositive();
        assertThat(provider.departures).isNotEmpty();
        assertThat(result.warnings().getFirst()).contains("전역 재탐색");
    }

    @Test
    void fallsBackBeforeExternalCallsWhenCandidateSafetyLimitIsExceeded() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        List<ScheduleCandidate> candidates = List.of(candidate(1, a), candidate(2, b));
        ScheduleRequest request = request(date, candidates);
        RouteProvider staticRoutes = (from, to, mode) -> route(from.equals(to) ? 0 : 10);
        MultiDaySchedule initial = new MultiDaySchedulePlanner(new ConstraintSchedulePlanner(staticRoutes))
                .plan(List.of(request), staticRoutes);
        TrafficMatrixProvider provider = new TrafficMatrixProvider();
        TimeDependentOptimizationProperties properties = properties();
        properties.setMaxCandidates(1);
        var optimizer = new TimeDependentGlobalScheduleOptimizer(
                provider, properties,
                new TimeDependentOptimizationMetrics(new SimpleMeterRegistry()));

        var result = optimizer.optimize(initial, List.of(request), ScheduleBudget.unlimited(),
                "Asia/Seoul", RouteDataType.GOOGLE_ROUTES);

        assertThat(result.applied()).isFalse();
        assertThat(result.schedule()).isSameAs(initial);
        assertThat(provider.departures).isEmpty();
        assertThat(result.warnings()).singleElement().asString().contains("candidate_limit");
    }

    @Test
    void reservesBudgetForUnvisitedMandatoryPlacesDuringGlobalSearch() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        ScheduleCandidate mandatory = candidate(1, a, 70, true);
        ScheduleCandidate optional = candidate(2, b, 100, false);
        List<ScheduleCandidate> candidates = List.of(mandatory, optional);
        ScheduleRequest request = request(date, candidates);
        RouteProvider staticRoutes = (from, to, mode) -> route(from.equals(to) ? 0 : 10);
        MultiDaySchedule initial = new MultiDaySchedulePlanner(new ConstraintSchedulePlanner(staticRoutes))
                .plan(List.of(request), staticRoutes, new ScheduleBudget(60L, Map.of(1L, 60L, 2L, 50L)));
        var optimizer = new TimeDependentGlobalScheduleOptimizer(
                new TrafficMatrixProvider(), properties(),
                new TimeDependentOptimizationMetrics(new SimpleMeterRegistry()));

        var result = optimizer.optimize(initial, List.of(request),
                new ScheduleBudget(60L, Map.of(1L, 60L, 2L, 50L)),
                "Asia/Seoul", RouteDataType.GOOGLE_ROUTES);

        assertThat(result.applied()).isTrue();
        assertThat(result.schedule().visits())
                .extracting(ScheduledVisit::tripPlaceId)
                .containsExactly(1L);
        assertThat(result.schedule().exclusions())
                .extracting(ExcludedVisit::reason)
                .containsExactly(ExclusionReason.BUDGET);
    }

    private TimeDependentOptimizationProperties properties() {
        TimeDependentOptimizationProperties properties = new TimeDependentOptimizationProperties();
        properties.setEnabled(true);
        properties.setMaxMatrixElements(1_000);
        return properties;
    }

    private ScheduleRequest request(LocalDate date, List<ScheduleCandidate> candidates) {
        return new ScheduleRequest(
                date, LocalTime.of(9, 0), LocalTime.of(15, 0), hotel, hotel,
                TransportMode.DRIVING, OptimizationAlgorithm.EXACT_SEARCH,
                candidates, candidates.stream().map(ScheduleCandidate::tripPlaceId).toList());
    }

    private ScheduleCandidate candidate(long id, Location location) {
        return candidate(id, location, 80, true);
    }

    private ScheduleCandidate candidate(long id, Location location, int priority, boolean mustVisit) {
        return new ScheduleCandidate(id, id, "장소 " + id, location, priority, mustVisit,
                null, null, false, null, null, 30);
    }

    private RouteResult route(int minutes) {
        return new RouteResult(minutes == 0 ? 0 : minutes * 100L, minutes);
    }

    private Location location(int coordinate) {
        return Location.of(BigDecimal.valueOf(coordinate), BigDecimal.valueOf(coordinate));
    }

    private final class TrafficMatrixProvider implements RouteMatrixProvider {
        private final List<Instant> departures = new java.util.ArrayList<>();

        @Override
        public RouteMatrix build(List<Location> locations, TransportMode mode) {
            return build(locations, mode, Instant.now().plusSeconds(3600));
        }

        @Override
        public RouteMatrix build(List<Location> locations, TransportMode mode, Instant departure) {
            departures.add(departure);
            Map<RouteMatrix.Leg, RouteResult> routes = new LinkedHashMap<>();
            for (Location origin : locations) {
                for (Location destination : locations) {
                    int minutes;
                    if (origin.equals(destination)) minutes = 0;
                    else if (origin.equals(hotel) && destination.equals(a)) minutes = 120;
                    else minutes = 10;
                    routes.put(new RouteMatrix.Leg(origin, destination), route(minutes));
                }
            }
            return new RouteMatrix(mode, RouteDataType.GOOGLE_ROUTES, routes, 1, 1);
        }
    }
}
