package com.routeplan.optimization.constraint;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.optimization.domain.*;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.place.search.LiveOpeningHours;
import com.routeplan.trip.domain.TransportMode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScheduleAccuracyTest {
    private final Location hotel = Location.of(BigDecimal.ZERO, BigDecimal.ZERO);
    private final LocalDate date = LocalDate.of(2026, 9, 10);
    private final RouteProvider simple = (from, to, mode) -> new RouteResult(from.equals(to) ? 0 : 100, from.equals(to) ? 0 : 10);
    private final MultiDaySchedulePlanner planner = new MultiDaySchedulePlanner(new ConstraintSchedulePlanner(simple));

    @Test void waitsThroughBreakAndDoesNotSplitTheStay() {
        var candidate = candidate(1, false).withOpeningWindows(List.of(new OpeningWindow(540, 570), new OpeningWindow(660, 780)));
        var request = request(List.of(candidate));
        var schedule = planner.plan(List.of(request), simple);
        assertThat(schedule.visits().getFirst().startTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(schedule.visits().getFirst().waitingMinutes()).isEqualTo(110);
        assertThat(candidate.earliestStart(550, LocalTime.of(9, 0), LocalTime.of(11, 30))).isEqualTo(-1);
    }

    @Test void specialDayClosureOverridesWeeklyHoursOnlyForProviderWindow() throws Exception {
        var parsed = LiveOpeningHours.parse(new ObjectMapper().readTree("""
                {"utcOffsetMinutes":540,"regularOpeningHours":{"periods":[{"open":{"day":0,"hour":0}}]},
                 "currentOpeningHours":{"periods":[
                   {"open":{"date":{"year":2026,"month":9,"day":11},"hour":10},
                    "close":{"date":{"year":2026,"month":9,"day":11},"hour":13}}
                 ],"specialDays":[{"date":{"year":2026,"month":9,"day":10}}]}}
                """), Instant.parse("2026-09-09T16:00:00Z"));
        assertThat(parsed.dates()).hasSize(7);
        assertThat(parsed.dates().get(date).closed()).isTrue();
        assertThat(parsed.dates().get(date.plusDays(1)).intervals()).containsExactly(new OpeningWindow(600, 780));
        assertThat(parsed.dates()).doesNotContainKey(date.plusDays(7));
        assertThat(parsed.days().get(DayOfWeek.THURSDAY).closed()).isFalse();
    }

    @Test void currentOvernightPeriodCoversBothDaysAndWeeklyWrapCoversSunday() throws Exception {
        var parsed = LiveOpeningHours.parse(new ObjectMapper().readTree("""
                {"utcOffsetMinutes":0,"regularOpeningHours":{"periods":[
                 {"open":{"day":6,"hour":22},"close":{"day":0,"hour":3}}]},
                 "currentOpeningHours":{"periods":[
                 {"open":{"date":{"year":2026,"month":9,"day":10},"hour":22},
                  "close":{"date":{"year":2026,"month":9,"day":11},"hour":3}}]}}
                """), Instant.parse("2026-09-10T12:00:00Z"));
        assertThat(parsed.days().get(DayOfWeek.SUNDAY).intervals()).containsExactly(new OpeningWindow(0, 180));
        assertThat(parsed.dates().get(date).intervals()).containsExactly(new OpeningWindow(1320, 1440));
        assertThat(parsed.dates().get(date.plusDays(1)).intervals()).containsExactly(new OpeningWindow(0, 180));
    }

    @Test void transitUsesEachActualDepartureAndCountsOnlyBoundedElements() {
        var request = request(List.of(candidate(1, true), candidate(2, true)));
        var initial = planner.plan(List.of(request), simple);
        List<Instant> departures = new ArrayList<>();
        var result = DepartureAwareScheduleRefiner.refine(initial, List.of(request), "Asia/Seoul", (from, to, at) -> {
            departures.add(at); return new RouteResult(500, 20);
        });
        assertThat(result.schedule().visits()).extracting(ScheduledVisit::startTime)
                .containsExactly(LocalTime.of(9, 20), LocalTime.of(10, 40));
        assertThat(departures).contains(Instant.parse("2026-09-10T00:00:00Z"), Instant.parse("2026-09-10T01:20:00Z"), Instant.parse("2026-09-10T02:40:00Z"));
        assertThat(result.schedule().returnArrivalTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(result.schedule().totalTravelMinutes()).isEqualTo(60);
        assertThat(result.calls()).isLessThanOrEqualTo(initial.visits().size() * 2 + initial.days().size());
    }

    @Test void actualTransitExcludesOptionalButRejectsMustVisit() {
        for (boolean mandatory : List.of(false, true)) {
            var constrained = candidate(1, mandatory).withOpeningWindows(List.of(new OpeningWindow(540, 615)));
            var request = request(List.of(constrained));
            var initial = planner.plan(List.of(request), simple);
            if (mandatory) {
                assertThatThrownBy(() -> DepartureAwareScheduleRefiner.refine(initial, List.of(request), "Asia/Seoul", (f, t, at) -> new RouteResult(500, 30)))
                        .isInstanceOf(InfeasibleScheduleException.class);
            } else {
                var result = DepartureAwareScheduleRefiner.refine(initial, List.of(request), "Asia/Seoul", (f, t, at) -> new RouteResult(500, 30));
                assertThat(result.schedule().visits()).isEmpty();
                assertThat(result.schedule().exclusions()).extracting(ExcludedVisit::reason).containsExactly(ExclusionReason.TIME_WINDOW);
            }
        }
    }

    private ScheduleCandidate candidate(long id, boolean must) {
        return new ScheduleCandidate(id, id, "장소 " + id, Location.of(BigDecimal.valueOf(id), BigDecimal.valueOf(id)),
                80, must, null, null, false, null, null, 60);
    }
    private ScheduleRequest request(List<ScheduleCandidate> candidates) {
        return new ScheduleRequest(date, LocalTime.of(9, 0), LocalTime.of(20, 0), hotel, hotel,
                TransportMode.PUBLIC_TRANSIT, OptimizationAlgorithm.EXACT_SEARCH, candidates, candidates.stream().map(ScheduleCandidate::tripPlaceId).toList());
    }
}
