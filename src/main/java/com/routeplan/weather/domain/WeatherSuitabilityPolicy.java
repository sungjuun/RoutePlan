package com.routeplan.weather.domain;

import com.routeplan.place.domain.PlaceEnvironment;
import org.springframework.stereotype.Component;

@Component
public class WeatherSuitabilityPolicy {

    public int adjustment(WeatherSnapshot weather, PlaceEnvironment environment) {
        if (weather.condition() == WeatherCondition.UNKNOWN) {
            return 0;
        }
        if (weather.condition() == WeatherCondition.EXTREME) {
            return switch (environment) {
                case INDOOR -> 30;
                case MIXED -> -10;
                case OUTDOOR -> -50;
            };
        }
        boolean wet = weather.condition() == WeatherCondition.RAIN
                || weather.condition() == WeatherCondition.SNOW
                || weather.precipitationProbability() >= 60;
        if (wet) {
            return switch (environment) {
                case INDOOR -> 25;
                case MIXED -> 0;
                case OUTDOOR -> -30;
            };
        }
        if (weather.condition() == WeatherCondition.CLEAR) {
            return switch (environment) {
                case INDOOR -> 0;
                case MIXED -> 5;
                case OUTDOOR -> 10;
            };
        }
        return switch (environment) {
            case INDOOR -> 5;
            case MIXED -> 3;
            case OUTDOOR -> 0;
        };
    }
}
