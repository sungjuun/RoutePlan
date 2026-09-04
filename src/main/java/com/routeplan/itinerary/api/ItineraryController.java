package com.routeplan.itinerary.api;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.itinerary.application.ItineraryOptimizationService;
import com.routeplan.itinerary.application.ManualItineraryEditingService;
import com.routeplan.itinerary.application.ManualItineraryEditingService.DayAssignment;
import com.routeplan.itinerary.application.ManualItineraryEditingService.ManualEditCommand;
import com.routeplan.itinerary.application.ManualItineraryEditingService.ManualEditPreview;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final ResourceAccessService accessService;
    private final ManualItineraryEditingService manualEditingService;

    public ItineraryController(
            ItineraryOptimizationService optimizationService,
            ItineraryReoptimizationService reoptimizationService,
            ItineraryQueryService queryService,
            ResourceAccessService accessService,
            ManualItineraryEditingService manualEditingService
    ) {
        this.optimizationService = optimizationService;
        this.reoptimizationService = reoptimizationService;
        this.queryService = queryService;
        this.accessService = accessService;
        this.manualEditingService = manualEditingService;
    }

    @PostMapping("/trips/{tripId}/itineraries/manual-edit/preview")
    public ManualEditPreview previewManualEdit(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ManualEditRequest request
    ) {
        accessService.requireTripEditor(tripId, principal.userId());
        return manualEditingService.preview(tripId, request.toCommand());
    }

    @PostMapping("/trips/{tripId}/itineraries/manual-edit")
    public ResponseEntity<ItineraryView> applyManualEdit(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ManualEditRequest request
    ) {
        accessService.requireTripEditor(tripId, principal.userId());
        ItineraryView result = manualEditingService.apply(tripId, request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/itineraries/" + result.itineraryId())).body(result);
    }

    @PostMapping("/trips/{tripId}/reoptimize")
    public ResponseEntity<ItineraryView> reoptimize(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "NEAREST_NEIGHBOR") OptimizationAlgorithm algorithm,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ReoptimizeRequest request
    ) {
        accessService.requireTripEditor(tripId, principal.userId());
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
            @RequestParam(defaultValue = "NEAREST_NEIGHBOR") OptimizationAlgorithm algorithm,
            @AuthenticationPrincipal RoutePlanPrincipal principal
    ) {
        accessService.requireTripEditor(tripId, principal.userId());
        ItineraryView result = optimizationService.optimize(tripId, algorithm);
        return ResponseEntity.created(
                URI.create("/api/v1/itineraries/" + result.itineraryId())
        ).body(result);
    }

    @GetMapping("/trips/{tripId}/itineraries/latest")
    public ItineraryView getLatest(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal
    ) {
        accessService.requireTripViewer(tripId, principal.userId());
        return queryService.getLatest(tripId);
    }

    @GetMapping("/itineraries/{itineraryId}")
    public ItineraryView get(
            @PathVariable Long itineraryId,
            @AuthenticationPrincipal RoutePlanPrincipal principal
    ) {
        accessService.requireItineraryViewer(itineraryId, principal.userId());
        return queryService.get(itineraryId);
    }

    public record ReoptimizeRequest(
            @NotNull @Positive Long sourceItineraryId,
            LocalDate currentDate,
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
                    currentDate,
                    currentTime,
                    currentLatitude,
                    currentLongitude,
                    completedItemIds,
                    reason,
                    reasonDetail
            );
        }
    }

    public record ManualEditRequest(
            @NotNull @Positive Long sourceItineraryId,
            @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid DayAssignmentRequest> assignments
    ) {
        ManualEditCommand toCommand() {
            return new ManualEditCommand(sourceItineraryId, assignments.stream()
                    .map(value -> new DayAssignment(value.visitDate(), value.itineraryItemIds())).toList());
        }
    }

    public record DayAssignmentRequest(
            @NotNull LocalDate visitDate,
            @NotNull @Size(max = 50) List<@NotNull @Positive Long> itineraryItemIds
    ) {}
}
