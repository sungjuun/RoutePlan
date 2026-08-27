package com.routeplan.weather.api;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.weather.application.TripWeatherService;
import com.routeplan.weather.application.TripWeatherService.ForecastCommand;
import com.routeplan.weather.application.TripWeatherService.ForecastResult;
import com.routeplan.weather.domain.WeatherCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/weather")
public class TripWeatherController {

    private final TripWeatherService weatherService;
    private final ResourceAccessService accessService;

    public TripWeatherController(
            TripWeatherService weatherService,
            ResourceAccessService accessService
    ) {
        this.weatherService = weatherService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<ForecastResult> get(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal
    ) {
        accessService.requireTripOwner(tripId, principal.userId());
        return weatherService.get(tripId);
    }

    @PutMapping
    public List<ForecastResult> replace(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ReplaceForecastsRequest request
    ) {
        accessService.requireTripOwner(tripId, principal.userId());
        return weatherService.replace(
                tripId,
                request.forecasts().stream().map(ForecastRequest::toCommand).toList()
        );
    }

    public record ReplaceForecastsRequest(
            @NotNull @Size(max = 14) List<@Valid ForecastRequest> forecasts
    ) {
    }

    public record ForecastRequest(
            @NotNull LocalDate forecastDate,
            @NotNull WeatherCondition condition,
            @Min(0) @Max(100) int precipitationProbability
    ) {

        ForecastCommand toCommand() {
            return new ForecastCommand(forecastDate, condition, precipitationProbability);
        }
    }
}
