package com.routeplan.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.user.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripTest {

    @Test
    void createsSingleDayTripAsDraft() {
        LocalDate travelDate = LocalDate.of(2026, 9, 10);

        Trip trip = Trip.create(
                User.create("traveler"),
                "오사카 하루 여행",
                travelDate,
                travelDate,
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.PUBLIC_TRANSIT
        );

        assertThat(trip.getStatus()).isEqualTo(TripStatus.DRAFT);
        assertThat(trip.getName()).isEqualTo("오사카 하루 여행");
    }

    @Test
    void createsTripUpToFourteenDays() {
        Trip trip = Trip.create(
                User.create("traveler"),
                "오사카 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 23),
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.PUBLIC_TRANSIT
        );

        assertThat(trip.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    void rejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> Trip.create(
                User.create("traveler"),
                "오사카 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 9),
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.PUBLIC_TRANSIT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일");
    }

    @Test
    void rejectsTripLongerThanFourteenDays() {
        assertThatThrownBy(() -> Trip.create(
                User.create("traveler"),
                "오사카 여행",
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 24),
                "난바 숙소",
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900"),
                TransportMode.PUBLIC_TRANSIT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("14일");
    }
}
