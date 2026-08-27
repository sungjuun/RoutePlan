package com.routeplan.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.place.domain.PlaceEnvironment;
import org.junit.jupiter.api.Test;

class WeatherSuitabilityPolicyTest {

    private final WeatherSuitabilityPolicy policy = new WeatherSuitabilityPolicy();

    @Test
    void prefersIndoorPlacesAndPenalizesOutdoorPlacesWhenRainIsLikely() {
        WeatherSnapshot rainy = new WeatherSnapshot(WeatherCondition.RAIN, 80);

        assertThat(policy.adjustment(rainy, PlaceEnvironment.INDOOR)).isEqualTo(25);
        assertThat(policy.adjustment(rainy, PlaceEnvironment.MIXED)).isZero();
        assertThat(policy.adjustment(rainy, PlaceEnvironment.OUTDOOR)).isEqualTo(-30);
    }

    @Test
    void prefersOutdoorPlacesOnClearDaysAndStaysNeutralWithoutForecast() {
        WeatherSnapshot clear = new WeatherSnapshot(WeatherCondition.CLEAR, 0);

        assertThat(policy.adjustment(clear, PlaceEnvironment.OUTDOOR)).isEqualTo(10);
        assertThat(policy.adjustment(clear, PlaceEnvironment.INDOOR)).isZero();
        assertThat(policy.adjustment(
                WeatherSnapshot.unknown(), PlaceEnvironment.OUTDOOR
        )).isZero();
    }

    @Test
    void stronglyPenalizesOutdoorPlacesInExtremeWeather() {
        WeatherSnapshot extreme = new WeatherSnapshot(WeatherCondition.EXTREME, 100);

        assertThat(policy.adjustment(extreme, PlaceEnvironment.INDOOR)).isEqualTo(30);
        assertThat(policy.adjustment(extreme, PlaceEnvironment.OUTDOOR)).isEqualTo(-50);
    }
}
