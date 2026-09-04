package com.routeplan.ai.api;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.ai.application.NaturalLanguageConstraintService;
import com.routeplan.ai.application.NaturalLanguageConstraintService.ApplyProposal;
import com.routeplan.ai.application.NaturalLanguageConstraintService.PlaceSettings;
import com.routeplan.ai.application.NaturalLanguageConstraintService.PreviewResult;
import com.routeplan.ai.application.NaturalLanguageConstraintService.TripSettings;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/natural-language")
public class NaturalLanguageConstraintController {

    private final NaturalLanguageConstraintService service;
    private final ResourceAccessService accessService;

    public NaturalLanguageConstraintController(
            NaturalLanguageConstraintService service,
            ResourceAccessService accessService
    ) {
        this.service = service;
        this.accessService = accessService;
    }

    @PostMapping("/preview")
    public PreviewResult preview(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody PreviewRequest request
    ) {
        accessService.requireTripViewer(tripId, principal.userId());
        return service.preview(tripId, request.text().trim());
    }

    @PostMapping("/apply")
    public TripResult apply(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ApplyRequest request
    ) {
        accessService.requireTripEditor(tripId, principal.userId());
        return service.apply(tripId, request.toProposal());
    }

    public record PreviewRequest(
            @NotBlank @Size(max = 2_000) String text
    ) {
    }

    public record ApplyRequest(
            @NotNull @Valid TripSettingsRequest trip,
            @NotNull @Size(max = 50) List<@Valid PlaceSettingsRequest> places
    ) {
        ApplyProposal toProposal() {
            return new ApplyProposal(
                    trip.toSettings(),
                    places.stream().map(PlaceSettingsRequest::toSettings).toList()
            );
        }
    }

    public record TripSettingsRequest(
            @NotNull LocalTime dailyStartTime,
            @NotNull LocalTime dailyEndTime,
            @NotNull TripPace pace,
            @NotNull TransportMode transportMode
    ) {
        TripSettings toSettings() {
            return new TripSettings(dailyStartTime, dailyEndTime, pace, transportMode);
        }
    }

    public record PlaceSettingsRequest(
            @NotNull Long placeId,
            @NotNull @Min(1) @Max(100) Integer priority,
            @NotNull Boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            @Min(1) @Max(1_440) Integer minimumStayMinutes,
            @Min(1) @Max(1_440) Integer maximumStayMinutes
    ) {
        PlaceSettings toSettings() {
            return new PlaceSettings(
                    placeId,
                    "",
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
