package com.routeplan.trip.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TripPaceTest {

    @Test
    void resolvesStayMinutesWithinConfiguredRange() {
        assertThat(TripPace.ACTIVE.stayMinutes(60, 45, 90)).isEqualTo(45);
        assertThat(TripPace.STANDARD.stayMinutes(60, 45, 90)).isEqualTo(60);
        assertThat(TripPace.RELAXED.stayMinutes(60, 45, 90)).isEqualTo(90);
    }

    @Test
    void derivesRangeFromAverageWhenOverridesAreMissing() {
        assertThat(TripPace.ACTIVE.stayMinutes(60, null, null)).isEqualTo(45);
        assertThat(TripPace.STANDARD.stayMinutes(60, null, null)).isEqualTo(60);
        assertThat(TripPace.RELAXED.stayMinutes(60, null, null)).isEqualTo(75);
    }
}
