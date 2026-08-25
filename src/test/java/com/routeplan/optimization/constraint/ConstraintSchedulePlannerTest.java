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

class ConstraintSchedulePlannerTest {

    private static final Location ACCOMMODATION = location(0, 0);
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 9, 10);

    private final ConstraintSchedulePlanner planner = new ConstraintSchedulePlanner(
            new FixedRouteProvider()
    );

    @Test
    void waitsUntilOpeningAndReturnsToAccommodationBeforeDailyEnd() {
        ScheduleCandidate candidate = candidate(
                1, 101, "오사카성", location(1, 1), 80, true,
                LocalTime.of(10, 0), LocalTime.of(12, 0), false, 60
        );

        ConstraintSchedule result = planner.plan(request(
                LocalTime.of(9, 0), LocalTime.of(12, 0), List.of(candidate)
        ));

        assertThat(result.visits()).hasSize(1);
        ScheduledVisit visit = result.visits().getFirst();
        assertThat(visit.arrivalTime()).isEqualTo(LocalTime.of(9, 10));
        assertThat(visit.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(visit.endTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(visit.waitingMinutes()).isEqualTo(50);
        assertThat(result.returnArrivalTime()).isEqualTo(LocalTime.of(11, 10));
        assertThat(result.returnTravelMinutes()).isEqualTo(10);
        assertThat(result.totalTravelMinutes()).isEqualTo(20);
    }

    @Test
    void keepsHigherPriorityPlaceAndRecordsLowerPriorityExclusion() {
        ScheduleCandidate high = candidate(
                1, 101, "높은 우선순위", ACCOMMODATION, 100, false,
                null, null, false, 90
        );
        ScheduleCandidate low = candidate(
                2, 102, "낮은 우선순위", ACCOMMODATION, 10, false,
                null, null, false, 90
        );

        ConstraintSchedule result = planner.plan(request(
                LocalTime.of(9, 0), LocalTime.of(11, 0), List.of(low, high)
        ));

        assertThat(result.visits())
                .extracting(ScheduledVisit::placeId)
                .containsExactly(101L);
        assertThat(result.exclusions()).containsExactly(
                new ExcludedVisit(102, "낮은 우선순위", 10, ExclusionReason.DAILY_LIMIT)
        );
        assertThat(result.visitedPriorityScore()).isEqualTo(100);
    }

    @Test
    void rejectsClosedMustVisitWithStructuredViolation() {
        ScheduleCandidate closed = candidate(
                1, 101, "휴무 장소", location(1, 1), 100, true,
                null, null, true, 60
        );

        assertThatThrownBy(() -> planner.plan(request(
                LocalTime.of(9, 0), LocalTime.of(20, 0), List.of(closed)
        )))
                .isInstanceOfSatisfying(InfeasibleScheduleException.class, exception -> {
                    assertThat(exception.violations()).hasSize(1);
                    assertThat(exception.violations().getFirst().reason())
                            .isEqualTo(ExclusionReason.CLOSED);
                    assertThat(exception.getMessage()).contains("휴무 장소");
                });
    }

    @Test
    void startsAtCurrentLocationAndReturnsToAccommodationWithNoRemainingPlaces() {
        Location currentLocation = location(2, 2);
        ScheduleRequest request = new ScheduleRequest(
                VISIT_DATE,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                currentLocation,
                ACCOMMODATION,
                TransportMode.WALKING,
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                List.of(),
                List.of()
        );

        ConstraintSchedule result = planner.plan(request);

        assertThat(result.visits()).isEmpty();
        assertThat(result.returnTravelDistanceMeters()).isEqualTo(100);
        assertThat(result.returnTravelMinutes()).isEqualTo(10);
        assertThat(result.returnArrivalTime()).isEqualTo(LocalTime.of(12, 10));
    }

    @Test
    void rejectsReturnFromCurrentLocationAfterDailyEndWithNoRemainingPlaces() {
        ScheduleRequest request = new ScheduleRequest(
                VISIT_DATE,
                LocalTime.of(12, 0),
                LocalTime.of(12, 5),
                location(2, 2),
                ACCOMMODATION,
                TransportMode.WALKING,
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                List.of(),
                List.of()
        );

        assertThatThrownBy(() -> planner.plan(request))
                .isInstanceOf(InfeasibleReturnException.class)
                .hasMessageContaining("숙소로 돌아갈 수 없습니다");
    }

    private ScheduleRequest request(
            LocalTime start,
            LocalTime end,
            List<ScheduleCandidate> candidates
    ) {
        return new ScheduleRequest(
                VISIT_DATE,
                start,
                end,
                ACCOMMODATION,
                ACCOMMODATION,
                TransportMode.WALKING,
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                candidates,
                candidates.stream().map(ScheduleCandidate::tripPlaceId).toList()
        );
    }

    private static ScheduleCandidate candidate(
            long tripPlaceId,
            long placeId,
            String name,
            Location location,
            int priority,
            boolean mustVisit,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed,
            int stayMinutes
    ) {
        return new ScheduleCandidate(
                tripPlaceId,
                placeId,
                name,
                location,
                priority,
                mustVisit,
                openTime,
                closeTime,
                closed,
                null,
                null,
                stayMinutes
        );
    }

    private static Location location(int latitude, int longitude) {
        return Location.of(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private static final class FixedRouteProvider implements RouteProvider {

        @Override
        public RouteResult getRoute(Location origin, Location destination, TransportMode mode) {
            return origin.equals(destination) ? new RouteResult(0, 0) : new RouteResult(100, 10);
        }
    }
}
