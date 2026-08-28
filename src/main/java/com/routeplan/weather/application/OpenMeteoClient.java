package com.routeplan.weather.application;

import com.fasterxml.jackson.databind.*;
import com.routeplan.integration.google.*;
import com.routeplan.weather.domain.WeatherCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;

@Component
public class OpenMeteoClient {
    private final URI baseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, Forecast> cache = new LinkedHashMap<>();

    public OpenMeteoClient(@Value("${routeplan.weather.base-url:https://api.open-meteo.com}") URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public synchronized Forecast fetch(BigDecimal latitude, BigDecimal longitude) {
        String key = latitude + "," + longitude;
        Forecast cached = cache.get(key);
        if (cached != null && cached.fetchedAt().isAfter(Instant.now().minusSeconds(900))) return cached;
        URI uri = baseUrl.resolve("/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&daily=weather_code,precipitation_probability_max&timezone=auto&forecast_days=16");
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "RoutePlan/1.0 weather integration")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("weather provider HTTP " + response.statusCode());
            Forecast value = parse(new ObjectMapper().readTree(response.body()), Instant.now());
            if (cache.size() >= 256) cache.remove(cache.keySet().iterator().next());
            cache.put(key, value);
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception e) { throw unavailable(); }
    }

    public static Forecast parse(JsonNode json, Instant fetchedAt) {
        String zone = ZoneId.of(json.path("timezone").asText()).getId();
        JsonNode daily = json.path("daily");
        var days = new ArrayList<Day>();
        for (int i = 0; i < daily.path("time").size(); i++) {
            JsonNode code = daily.path("weather_code").path(i);
            JsonNode probability = daily.path("precipitation_probability_max").path(i);
            if (!code.isIntegralNumber() || !probability.isNumber()) continue;
            int chance = probability.intValue();
            if (chance < 0 || chance > 100) throw new IllegalArgumentException("invalid weather probability");
            int wmo = code.intValue();
            if (!Set.of(0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,99).contains(wmo)) {
                throw new IllegalArgumentException("invalid WMO weather code");
            }
            WeatherCondition condition = wmo == 0 ? WeatherCondition.CLEAR
                    : wmo <= 48 ? WeatherCondition.CLOUDY
                    : wmo >= 95 ? WeatherCondition.EXTREME
                    : (wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86 ? WeatherCondition.SNOW
                    : WeatherCondition.RAIN;
            days.add(new Day(LocalDate.parse(daily.path("time").get(i).asText()), condition, chance));
        }
        if (days.isEmpty()) throw new IllegalArgumentException("empty weather forecast");
        return new Forecast(zone, List.copyOf(days), fetchedAt);
    }

    private ExternalProviderException unavailable() {
        return new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE,
                "자동 날씨 조회에 실패했습니다. 기존 예보는 유지됩니다. 잠시 후 다시 시도해 주세요.");
    }
    public record Day(LocalDate date, WeatherCondition condition, int probability) {}
    public record Forecast(String timeZoneId, List<Day> days, Instant fetchedAt) {}
}
