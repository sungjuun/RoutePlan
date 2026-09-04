package com.routeplan.integration;

import com.routeplan.auth.*;
import com.routeplan.common.error.*;
import com.routeplan.place.search.LiveOpeningHours;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.weather.application.AutomaticWeatherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class LiveDataController {
    private final ResourceAccessService access;
    private final AutomaticWeatherService weather;
    private final LiveOpeningHours hours;
    private final TripPlaceRepository places;
    private final ExternalUsageGuard usage;
    private final ExternalOperationsService operations;
    public LiveDataController(ResourceAccessService access, AutomaticWeatherService weather,
            LiveOpeningHours hours, TripPlaceRepository places, ExternalUsageGuard usage,
            ExternalOperationsService operations) {
        this.access = access; this.weather = weather; this.hours = hours; this.places = places;
        this.usage = usage; this.operations = operations;
    }
    @GetMapping("/trips/{tripId}/time-zone")
    public Map<String, String> zone(@PathVariable long tripId, @AuthenticationPrincipal RoutePlanPrincipal user) {
        access.requireTripViewer(tripId, user.userId()); return Map.of("timeZoneId", weather.zone(tripId));
    }
    @PutMapping("/trips/{tripId}/time-zone")
    public Map<String, String> zone(@PathVariable long tripId, @AuthenticationPrincipal RoutePlanPrincipal user,
            @Valid @RequestBody ZoneRequest request) {
        access.requireTripEditor(tripId, user.userId()); weather.setZone(tripId, request.timeZoneId());
        return Map.of("timeZoneId", weather.zone(tripId));
    }
    @PostMapping("/trips/{tripId}/weather/refresh")
    public AutomaticWeatherService.RefreshResult weather(@PathVariable long tripId, @AuthenticationPrincipal RoutePlanPrincipal user) {
        access.requireTripEditor(tripId, user.userId()); return weather.refresh(tripId);
    }
    @PostMapping("/trips/{tripId}/places/{placeId}/opening-hours/refresh")
    public LiveOpeningHours.Hours hours(@PathVariable long tripId, @PathVariable long placeId,
            @AuthenticationPrincipal RoutePlanPrincipal user) {
        access.requireTripEditor(tripId, user.userId());
        var place = places.findByTripIdAndPlaceId(tripId, placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        return hours.fetch(place.getPlace().getExternalPlaceId());
    }
    @GetMapping("/integrations/usage")
    public List<ExternalUsageGuard.Usage> usage() { return usage.current(); }
    @GetMapping("/integrations/operations")
    public ExternalOperationsService.Snapshot operations() { return operations.current(); }
    public record ZoneRequest(@NotBlank @Size(max = 100) String timeZoneId) {}
}
