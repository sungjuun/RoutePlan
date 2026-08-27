package com.routeplan.weather.domain;

import java.util.Objects;

public record WeatherSnapshot(
        WeatherCondition condition,
        int precipitationProbability
) {

    public WeatherSnapshot {
        Objects.requireNonNull(condition, "날씨 상태는 필수입니다.");
        if (precipitationProbability < 0 || precipitationProbability > 100) {
            throw new IllegalArgumentException("강수확률은 0 이상 100 이하여야 합니다.");
        }
    }

    public static WeatherSnapshot unknown() {
        return new WeatherSnapshot(WeatherCondition.UNKNOWN, 0);
    }
}
