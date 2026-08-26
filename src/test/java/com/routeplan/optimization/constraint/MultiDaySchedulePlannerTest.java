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
import org.junit.jupiter.api.Test;

class MultiDaySchedulePlannerTest {

    private static final Location HOTEL = Location.of(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 9, 10);
    private final RouteProvider routes = (origin, destination, mode) -> new RouteResult(0, 0);
    private final MultiDaySchedulePlanner planner = new MultiDaySchedulePlanner(
            new ConstraintSchedulePlanner(routes)
    );

    @Test
    void carriesUnscheduledPlacesToTheNextDayAndReturnsToHotelEveryDay() {
        ScheduleCandidate optional = candidate(1, 101, "첫날 장소", 80, false, false);
        ScheduleCandidate closedMust = candidate(2, 102, "둘째 날 필수", 100, true, true);
        ScheduleCandidate openMust = candidate(2, 102, "둘째 날 필수", 100, true, false);

        MultiDaySchedule schedule = planner.plan(
                List.of(
                        request(FIRST_DAY, List.of(optional, closedMust)),
                        request(FIRST_DAY.plusDays(1), List.of(optional, openMust))
                ),
                routes
        );

        assertThat(schedule.days()).hasSize(2);
        assertThat(schedule.days().get(0).visits())
                .extracting(ScheduledVisit::placeId)
                .containsExactly(101L);
        assertThat(schedule.days().get(1).visits())
                .extracting(ScheduledVisit::placeId)
                .containsExactly(102L);
        assertThat(schedule.visits())
                .extracting(ScheduledVisit::sequence)
                .containsExactly(1, 2);
        assertThat(schedule.exclusions()).isEmpty();
        assertThat(schedule.days())
                .extracting(DailySchedule::returnArrivalTime)
                .containsExactly(LocalTime.of(10, 30), LocalTime.of(10, 30));
    }

    @Test
    void rejectsMustVisitClosedForTheWholeTrip() {
        ScheduleCandidate closed = candidate(1, 101, "계속 휴무", 100, true, true);

        assertThatThrownBy(() -> planner.plan(
                List.of(
                        request(FIRST_DAY, List.of(closed)),
                        request(FIRST_DAY.plusDays(1), List.of(closed))
                ),
                routes
        )).isInstanceOfSatisfying(InfeasibleScheduleException.class, exception -> {
            assertThat(exception.violations()).hasSize(1);
            assertThat(exception.violations().getFirst().reason())
                    .isEqualTo(ExclusionReason.CLOSED);
            assertThat(exception.violations().getFirst().message())
                    .contains("여행 기간");
        });
    }

    private ScheduleRequest request(LocalDate date, List<ScheduleCandidate> candidates) {
        return new ScheduleRequest(
                date,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                HOTEL,
                HOTEL,
                TransportMode.WALKING,
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                candidates,
                candidates.stream().map(ScheduleCandidate::tripPlaceId).toList()
        );
    }

    private ScheduleCandidate candidate(
            long tripPlaceId,
            long placeId,
            String name,
            int priority,
            boolean mustVisit,
            boolean closed
    ) {
        return new ScheduleCandidate(
                tripPlaceId,
                placeId,
                name,
                HOTEL,
                priority,
                mustVisit,
                null,
                null,
                closed,
                null,
                null,
                90
        );
    }
}
