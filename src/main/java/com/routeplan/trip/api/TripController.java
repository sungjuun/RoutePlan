package com.routeplan.trip.api;

import com.routeplan.trip.application.TripService;
import com.routeplan.trip.application.TripService.CreateTripCommand;
import com.routeplan.trip.application.TripService.AddTripPlaceCommand;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.application.TripService.UpdateTripCommand;
import com.routeplan.trip.application.TripService.UpdateTripPlaceCommand;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResult> create(@Valid @RequestBody CreateTripRequest request) {
        TripResult result = tripService.create(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/trips/" + result.id())).body(result);
    }

    @GetMapping("/{tripId}")
    public TripResult get(@PathVariable Long tripId) {
        return tripService.get(tripId);
    }

    @PatchMapping("/{tripId}")
    public TripResult update(@PathVariable Long tripId, @Valid @RequestBody UpdateTripRequest request) {
        return tripService.update(tripId, request.toCommand());
    }

    @PostMapping("/{tripId}/places")
    public ResponseEntity<TripResult> addPlace(
            @PathVariable Long tripId,
            @Valid @RequestBody AddTripPlaceRequest request
    ) {
        TripResult result = tripService.addPlace(tripId, request.toCommand());
        return ResponseEntity.created(
                URI.create("/api/v1/trips/" + tripId + "/places/" + request.placeId())
        ).body(result);
    }

    @PatchMapping("/{tripId}/places/{placeId}")
    public TripResult updatePlaceConstraints(
            @PathVariable Long tripId,
            @PathVariable Long placeId,
            @Valid @RequestBody UpdateTripPlaceRequest request
    ) {
        return tripService.updatePlaceConstraints(tripId, placeId, request.toCommand());
    }

    @DeleteMapping("/{tripId}/places/{placeId}")
    public ResponseEntity<Void> removePlace(@PathVariable Long tripId, @PathVariable Long placeId) {
        tripService.removePlace(tripId, placeId);
        return ResponseEntity.noContent().build();
    }

    public record CreateTripRequest(
            @NotNull Long userId,
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            @NotBlank @Size(max = 100) String accommodationName,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal accommodationLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal accommodationLongitude,
            @NotNull TransportMode transportMode,
            TripPace pace
    ) {
        CreateTripCommand toCommand() {
            return new CreateTripCommand(
                    userId, name, startDate, endDate, accommodationName,
                    accommodationLatitude, accommodationLongitude, transportMode,
                    dailyStartTime, dailyEndTime, pace
            );
        }
    }

    public record UpdateTripRequest(
            @Size(min = 1, max = 100) String name,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            @Size(min = 1, max = 100) String accommodationName,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal accommodationLatitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal accommodationLongitude,
            TransportMode transportMode,
            TripPace pace
    ) {
        UpdateTripCommand toCommand() {
            return new UpdateTripCommand(
                    name, startDate, endDate, accommodationName,
                    accommodationLatitude, accommodationLongitude, transportMode,
                    dailyStartTime, dailyEndTime, pace
            );
        }
    }

    public record AddTripPlaceRequest(
            @NotNull Long placeId,
            @Min(1) @Max(100) Integer priority,
            Boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            @Min(1) @Max(1_440) Integer minimumStayMinutes,
            @Min(1) @Max(1_440) Integer maximumStayMinutes
    ) {

        AddTripPlaceCommand toCommand() {
            return new AddTripPlaceCommand(
                    placeId,
                    priority == null ? 50 : priority,
                    mustVisit != null && mustVisit,
                    preferredStartTime,
                    preferredEndTime,
                    minimumStayMinutes,
                    maximumStayMinutes
            );
        }
    }

    public record UpdateTripPlaceRequest(
            @NotNull @Min(1) @Max(100) Integer priority,
            @NotNull Boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            @Min(1) @Max(1_440) Integer minimumStayMinutes,
            @Min(1) @Max(1_440) Integer maximumStayMinutes
    ) {

        UpdateTripPlaceCommand toCommand() {
            return new UpdateTripPlaceCommand(
                    priority,
                    mustVisit,
                    preferredStartTime,
                    preferredEndTime,
                    minimumStayMinutes,
                    maximumStayMinutes
            );
        }
    }
}
