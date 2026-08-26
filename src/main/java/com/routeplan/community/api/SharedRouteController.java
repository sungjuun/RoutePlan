package com.routeplan.community.api;

import com.routeplan.community.application.RouteLikeView;
import com.routeplan.community.application.SharedRouteDetailView;
import com.routeplan.community.application.SharedRoutePageView;
import com.routeplan.community.application.SharedRouteService;
import com.routeplan.community.application.SharedRouteService.CopyCommand;
import com.routeplan.community.application.SharedRouteService.PublishCommand;
import com.routeplan.community.domain.SharedRouteSort;
import com.routeplan.community.domain.SharedRouteVisibility;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class SharedRouteController {

    private final SharedRouteService sharedRouteService;

    public SharedRouteController(SharedRouteService sharedRouteService) {
        this.sharedRouteService = sharedRouteService;
    }

    @PostMapping("/itineraries/{itineraryId}/share")
    public ResponseEntity<SharedRouteDetailView> publish(
            @PathVariable Long itineraryId,
            @Valid @RequestBody PublishRouteRequest request
    ) {
        SharedRouteDetailView route = sharedRouteService.publish(itineraryId, request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/routes/" + route.routeId())).body(route);
    }

    @GetMapping("/routes")
    public SharedRoutePageView discover(
            @RequestParam(required = false) @Size(max = 100) String region,
            @RequestParam(required = false) @Positive Integer travelDays,
            @RequestParam(defaultValue = "LATEST") SharedRouteSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size
    ) {
        return sharedRouteService.discover(region, travelDays, sort, page, size);
    }

    @GetMapping("/routes/{routeId}")
    public SharedRouteDetailView get(
            @PathVariable Long routeId,
            @RequestParam(required = false) @Positive Long viewerUserId
    ) {
        return sharedRouteService.get(routeId, viewerUserId);
    }

    @PostMapping("/routes/{routeId}/likes")
    public ResponseEntity<RouteLikeView> like(
            @PathVariable Long routeId,
            @Valid @RequestBody RouteLikeRequest request
    ) {
        return ResponseEntity.status(201).body(sharedRouteService.like(routeId, request.userId()));
    }

    @DeleteMapping("/routes/{routeId}/likes")
    public RouteLikeView unlike(
            @PathVariable Long routeId,
            @RequestParam @Positive Long userId
    ) {
        return sharedRouteService.unlike(routeId, userId);
    }

    @PostMapping("/routes/{routeId}/copy")
    public ResponseEntity<TripResult> copy(
            @PathVariable Long routeId,
            @Valid @RequestBody CopyRouteRequest request
    ) {
        TripResult trip = sharedRouteService.copy(routeId, request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/trips/" + trip.id())).body(trip);
    }

    public record PublishRouteRequest(
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String region,
            SharedRouteVisibility visibility
    ) {

        PublishCommand toCommand() {
            return new PublishCommand(userId, title, description, region, visibility);
        }
    }

    public record RouteLikeRequest(@NotNull @Positive Long userId) {
    }

    public record CopyRouteRequest(
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalTime dailyStartTime,
            @NotNull LocalTime dailyEndTime,
            @NotBlank @Size(max = 100) String accommodationName,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal accommodationLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal accommodationLongitude,
            @NotNull TransportMode transportMode,
            @NotNull TripPace pace
    ) {

        CopyCommand toCommand() {
            return new CopyCommand(
                    userId,
                    name,
                    startDate,
                    dailyStartTime,
                    dailyEndTime,
                    accommodationName,
                    accommodationLatitude,
                    accommodationLongitude,
                    transportMode,
                    pace
            );
        }
    }
}
