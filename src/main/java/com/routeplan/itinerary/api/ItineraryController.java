package com.routeplan.itinerary.api;

import com.routeplan.itinerary.application.ItineraryOptimizationService;
import com.routeplan.itinerary.application.ItineraryQueryService;
import com.routeplan.itinerary.application.ItineraryReoptimizationService;
import com.routeplan.itinerary.application.ItineraryReoptimizationService.ReoptimizeCommand;
import com.routeplan.itinerary.application.ItineraryView;
import com.routeplan.itinerary.domain.ItineraryChangeReason;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ItineraryController {

    private final ItineraryOptimizationService optimizationService;
    private final ItineraryReoptimizationService reoptimizationService;
    private final ItineraryQueryService queryService;

    public ItineraryController(
            ItineraryOptimizationService optimizationService,
            ItineraryReoptimizationService reoptimizationService,
            ItineraryQueryService queryService
    ) {
        this.optimizationService = optimizationService;
        this.reoptimizationService = reoptimizationService;
        this.queryService = queryService;
    }

    @PostMapping("/trips/{tripId}/reoptimize")
    public ResponseEntity<ItineraryView> reoptimize(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "NEAREST_NEIGHBOR") OptimizationAlgorithm algorithm,
            @Valid @RequestBody ReoptimizeRequest request
    ) {
        ItineraryView result = reoptimizationService.reoptimize(
                tripId,
                algorithm,
                request.toCommand()
        );
        return ResponseEntity.created(
                URI.create("/api/v1/itineraries/" + result.itineraryId())
        ).body(result);
    }

    @PostMapping("/trips/{tripId}/optimize")
    public ResponseEntity<ItineraryView> optimize(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "NEAREST_NEIGHBOR") OptimizationAlgorithm algorithm
    ) {
        ItineraryView result = optimizationService.optimize(tripId, algorithm);
        return ResponseEntity.created(
                URI.create("/api/v1/itineraries/" + result.itineraryId())
        ).body(result);
    }

    @GetMapping("/trips/{tripId}/itineraries/latest")
    public ItineraryView getLatest(@PathVariable Long tripId) {
        return queryService.getLatest(tripId);
    }

    @GetMapping("/itineraries/{itineraryId}")
    public ItineraryView get(@PathVariable Long itineraryId) {
        return queryService.get(itineraryId);
    }

    public record ReoptimizeRequest(
            @NotNull @Positive Long sourceItineraryId,
            @NotNull LocalTime currentTime,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal currentLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal currentLongitude,
            @NotNull @Size(max = 50) List<@NotNull @Positive Long> completedItemIds,
            @NotNull ItineraryChangeReason reason,
            @Size(max = 500) String reasonDetail
    ) {

        ReoptimizeCommand toCommand() {
            return new ReoptimizeCommand(
                    sourceItineraryId,
                    currentTime,
                    currentLatitude,
                    currentLongitude,
                    completedItemIds,
                    reason,
                    reasonDetail
            );
        }
    }
}
