package com.routeplan.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.place.domain.Place;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SharedRouteTest {

    @Test
    void preservesPublishedTripAndScheduleSnapshot() {
        User owner = User.create("snapshot-owner");
        Trip trip = Trip.create(
                owner,
                "오사카 원본 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 10),
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.WALKING,
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                TripPace.RELAXED
        );
        Place place = Place.create(
                "오사카성",
                new BigDecimal("34.687300"),
                new BigDecimal("135.526200"),
                "ATTRACTION",
                90
        );
        Itinerary itinerary = Itinerary.create(
                trip,
                1,
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                2_500,
                35
        );
        itinerary.addItem(
                place,
                1,
                2_500,
                35,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(9, 35),
                LocalTime.of(9, 35),
                LocalTime.of(11, 5),
                0,
                90,
                100,
                true
        );

        SharedRoute route = SharedRoute.publish(
                owner,
                itinerary,
                "오사카 핵심 하루",
                "걷기 좋은 핵심 동선",
                "오사카",
                SharedRouteVisibility.PUBLIC
        );
        trip.update(
                "수정된 원본 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 10),
                "우메다 숙소",
                new BigDecimal("34.705500"),
                new BigDecimal("135.498300"),
                TransportMode.PUBLIC_TRANSIT,
                LocalTime.of(10, 0),
                LocalTime.of(21, 0),
                TripPace.ACTIVE
        );

        assertThat(route.getSourceTripName()).isEqualTo("오사카 원본 여행");
        assertThat(route.getAccommodationName()).isEqualTo("난바 숙소");
        assertThat(route.getTransportMode()).isEqualTo(TransportMode.WALKING);
        assertThat(route.getPace()).isEqualTo(TripPace.RELAXED);
        assertThat(route.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getPlaceName()).isEqualTo("오사카성");
            assertThat(item.getStartTime()).isEqualTo(LocalTime.of(9, 35));
            assertThat(item.getStayMinutes()).isEqualTo(90);
            assertThat(item.isMustVisit()).isTrue();
        });
    }

    @Test
    void rejectsItineraryWithoutCompletedScheduleData() {
        User owner = User.create("incomplete-owner");
        Trip trip = Trip.create(
                owner,
                "미완성 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 10),
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.WALKING
        );
        Place place = Place.create(
                "오사카성",
                new BigDecimal("34.687300"),
                new BigDecimal("135.526200"),
                "ATTRACTION"
        );
        Itinerary itinerary = Itinerary.create(
                trip, 1, OptimizationAlgorithm.NEAREST_NEIGHBOR, 2_500, 35
        );
        itinerary.addItem(place, 1, 2_500, 35);

        assertThatThrownBy(() -> SharedRoute.publish(
                owner,
                itinerary,
                "미완성 루트",
                null,
                "오사카",
                SharedRouteVisibility.PUBLIC
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시간표가 완성되지 않은 일정");
    }

    @Test
    void maintainsViewCopyAndLikeCounters() {
        User owner = User.create("counter-owner");
        Trip trip = Trip.create(
                owner,
                "카운터 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 10),
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.WALKING
        );
        Place place = Place.create(
                "도톤보리",
                new BigDecimal("34.668700"),
                new BigDecimal("135.501300"),
                "FOOD"
        );
        Itinerary itinerary = Itinerary.create(
                trip, 1, OptimizationAlgorithm.NEAREST_NEIGHBOR, 500, 10
        );
        itinerary.addItem(
                place, 1, 500, 10,
                LocalDate.of(2026, 9, 10),
                LocalTime.of(9, 10),
                LocalTime.of(9, 10),
                LocalTime.of(10, 10),
                0, 60, 50, false
        );
        SharedRoute route = SharedRoute.publish(
                owner, itinerary, "카운터 루트", null, "오사카", SharedRouteVisibility.PUBLIC
        );

        route.increaseViewCount();
        route.increaseCopyCount();
        route.increaseLikeCount();
        route.decreaseLikeCount();

        assertThat(route.getViewCount()).isEqualTo(1);
        assertThat(route.getCopyCount()).isEqualTo(1);
        assertThat(route.getLikeCount()).isZero();
        assertThatThrownBy(route::decreaseLikeCount)
                .isInstanceOf(IllegalStateException.class);
    }
}
