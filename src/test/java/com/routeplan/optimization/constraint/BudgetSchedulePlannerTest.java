package com.routeplan.optimization.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetSchedulePlannerTest {

    private static final Location HOTEL = Location.of(BigDecimal.ZERO, BigDecimal.ZERO);
    private final RouteProvider routes = (origin, destination, mode) -> new RouteResult(0, 0);
    private final MultiDaySchedulePlanner planner = new MultiDaySchedulePlanner(new ConstraintSchedulePlanner(routes));

    @Test
    void reservesBudgetForMustVisitOnLaterDay() {
        ScheduleCandidate expensive = candidate(1, 90, false, false);
        ScheduleCandidate cheap = candidate(2, 70, false, false);
        MultiDaySchedule result = planner.plan(List.of(
                day(10, List.of(expensive, cheap, candidate(3, 100, true, true))),
                day(11, List.of(expensive, cheap, candidate(3, 100, true, false)))
        ), routes, new ScheduleBudget(100L, Map.of(1L, 80L, 2L, 30L, 3L, 70L)));

        assertThat(result.days().getFirst().visits()).extracting(ScheduledVisit::placeId).containsExactly(2L);
        assertThat(result.days().getLast().visits()).extracting(ScheduledVisit::placeId).containsExactly(3L);
        assertThat(result.exclusions()).containsExactly(new ExcludedVisit(1, "장소 1", 90, ExclusionReason.BUDGET));
    }

    @Test
    void doesNotResetBudgetEachDay() {
        List<ScheduleCandidate> places = List.of(candidate(1, 90, false, false), candidate(2, 70, false, false));
        MultiDaySchedule result = planner.plan(List.of(day(10, places), day(11, places)), routes,
                new ScheduleBudget(60L, Map.of(1L, 60L, 2L, 60L)));
        assertThat(result.visits()).extracting(ScheduledVisit::placeId).containsExactly(1L);
        assertThat(result.exclusions()).extracting(ExcludedVisit::reason).containsExactly(ExclusionReason.BUDGET);
    }

    @Test
    void rejectsUnknownPricesInsteadOfAssumingTheyAreFree() {
        assertThatThrownBy(() -> planner.plan(List.of(day(10, List.of(candidate(1, 50, false, false)))),
                routes, new ScheduleBudget(100L, Map.of())))
                .isInstanceOfSatisfying(BudgetConstraintException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(BudgetConstraintException.Reason.MISSING_COST));
    }

    @Test
    void rejectsUnaffordableMustVisits() {
        assertThatThrownBy(() -> planner.plan(List.of(day(10, List.of(candidate(1, 100, true, false)))),
                routes, new ScheduleBudget(50L, Map.of(1L, 51L))))
                .isInstanceOfSatisfying(BudgetConstraintException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(BudgetConstraintException.Reason.EXCEEDED));
    }

    @Test
    void acceptsFreeVisitWithZeroBudgetAndUsesCheaperTieBreak() {
        List<ScheduleCandidate> places = List.of(candidate(1, 50, false, false), candidate(2, 50, false, false));
        MultiDaySchedule result = planner.plan(List.of(day(10, places)), routes,
                new ScheduleBudget(0L, Map.of(1L, 1L, 2L, 0L)));
        assertThat(result.visits()).extracting(ScheduledVisit::placeId).containsExactly(2L);

        result = planner.plan(List.of(day(10, places)), routes,
                new ScheduleBudget(100L, Map.of(1L, 90L, 2L, 80L)));
        assertThat(result.visits()).extracting(ScheduledVisit::placeId).containsExactly(2L);
    }

    @Test
    void leavesUnbudgetedPrioritySelectionUnchanged() {
        List<ScheduleCandidate> places = List.of(candidate(1, 90, false, false), candidate(2, 50, false, false));
        MultiDaySchedule result = planner.plan(List.of(day(10, places)), routes,
                new ScheduleBudget(null, Map.of(1L, 999L)));
        assertThat(result.visits()).extracting(ScheduledVisit::placeId).containsExactly(1L);
    }

    private ScheduleRequest day(int day, List<ScheduleCandidate> candidates) {
        return new ScheduleRequest(LocalDate.of(2026, 9, day), LocalTime.of(9, 0), LocalTime.of(11, 0),
                HOTEL, HOTEL, TransportMode.WALKING, OptimizationAlgorithm.NEAREST_NEIGHBOR,
                candidates, candidates.stream().map(ScheduleCandidate::tripPlaceId).toList());
    }

    private ScheduleCandidate candidate(long id, int priority, boolean must, boolean closed) {
        return new ScheduleCandidate(id, id, "장소 " + id, HOTEL, priority, must,
                null, null, closed, null, null, 90);
    }
}
