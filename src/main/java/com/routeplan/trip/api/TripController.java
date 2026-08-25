package com.routeplan.trip.api;

import com.routeplan.trip.application.TripService;
import com.routeplan.trip.application.TripService.CreateTripCommand;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.application.TripService.UpdateTripCommand;
import com.routeplan.trip.domain.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
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
        TripResult result = tripService.addPlace(tripId, request.placeId());
        return ResponseEntity.created(
                URI.create("/api/v1/trips/" + tripId + "/places/" + request.placeId())
        ).body(result);
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
            @NotBlank @Size(max = 100) String accommodationName,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal accommodationLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal accommodationLongitude,
            @NotNull TransportMode transportMode
    ) {
        CreateTripCommand toCommand() {
            return new CreateTripCommand(
                    userId, name, startDate, endDate, accommodationName,
                    accommodationLatitude, accommodationLongitude, transportMode
            );
        }
    }

    public record UpdateTripRequest(
            @Size(min = 1, max = 100) String name,
            LocalDate startDate,
            LocalDate endDate,
            @Size(min = 1, max = 100) String accommodationName,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal accommodationLatitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
        UpdateTripCommand toCommand() {
            return new UpdateTripCommand(
                    name, startDate, endDate, accommodationName,
                    accommodationLatitude, accommodationLongitude, transportMode
            );
        }
    }

    public record AddTripPlaceRequest(@NotNull Long placeId) {
    }
}
