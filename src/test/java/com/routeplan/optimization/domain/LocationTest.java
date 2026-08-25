package com.routeplan.optimization.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void rejectsLatitudeOutsideEarthRange() {
        assertThatThrownBy(() -> new Location(90.000001, 127.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("위도");
    }

    @Test
    void rejectsNonFiniteLongitude() {
        assertThatThrownBy(() -> new Location(37.0, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("경도");
    }
}
