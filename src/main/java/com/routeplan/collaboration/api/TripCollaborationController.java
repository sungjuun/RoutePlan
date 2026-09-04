package com.routeplan.collaboration.api;

import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.budget.application.TripSpendingService.Category;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.collaboration.application.NearbyPlaceRecommendationService;
import com.routeplan.collaboration.application.NearbyPlaceRecommendationService.NearbyPlaceView;
import com.routeplan.collaboration.application.NearbyPlaceRecommendationService.NearbyQuery;
import com.routeplan.collaboration.application.TripCollaborationService;
import com.routeplan.collaboration.application.TripCollaborationService.CollaborationView;
import com.routeplan.collaboration.application.TripSettlementService;
import com.routeplan.collaboration.application.TripSettlementService.CreateSharedExpense;
import com.routeplan.collaboration.application.TripSettlementService.SettlementView;
import com.routeplan.collaboration.domain.TripMemberRole;
import com.routeplan.collaboration.domain.TripVoteValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
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
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
public class TripCollaborationController {

    private final TripCollaborationService collaboration;
    private final TripSettlementService settlements;
    private final NearbyPlaceRecommendationService nearby;

    public TripCollaborationController(
            TripCollaborationService collaboration,
            TripSettlementService settlements,
            NearbyPlaceRecommendationService nearby
    ) {
        this.collaboration = collaboration;
        this.settlements = settlements;
        this.nearby = nearby;
    }

    @GetMapping("/collaboration")
    public CollaborationView collaboration(
            @PathVariable long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal user
    ) {
        return collaboration.get(tripId, user.userId());
    }

    @PostMapping("/members")
    public ResponseEntity<CollaborationView> addMember(
            @PathVariable long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal user,
            @Valid @RequestBody AddMemberRequest request
    ) {
        CollaborationView result = collaboration.addMember(
                tripId, user.userId(), request.email(), request.role());
        return ResponseEntity.created(URI.create("/api/v1/trips/" + tripId + "/collaboration"))
                .body(result);
    }

    @PatchMapping("/members/{memberId}")
    public CollaborationView updateMember(
            @PathVariable long tripId,
            @PathVariable long memberId,
            @AuthenticationPrincipal RoutePlanPrincipal user,
            @Valid @RequestBody UpdateMemberRequest request
    ) {
        return collaboration.updateMember(tripId, user.userId(), memberId, request.role());
    }

    @DeleteMapping("/members/{memberId}")
    public CollaborationView removeMember(
            @PathVariable long tripId,
            @PathVariable long memberId,
            @AuthenticationPrincipal RoutePlanPrincipal user
    ) {
        return collaboration.removeMember(tripId, user.userId(), memberId);
    }

    @PutMapping("/places/{placeId}/vote")
    public CollaborationView vote(
            @PathVariable long tripId,
            @PathVariable long placeId,
            @AuthenticationPrincipal RoutePlanPrincipal user,
            @Valid @RequestBody VoteRequest request
    ) {
        return collaboration.vote(tripId, user.userId(), placeId, request.value());
    }

    @DeleteMapping("/places/{placeId}/vote")
    public CollaborationView removeVote(
            @PathVariable long tripId,
            @PathVariable long placeId,
            @AuthenticationPrincipal RoutePlanPrincipal user
    ) {
        return collaboration.removeVote(tripId, user.userId(), placeId);
    }

    @GetMapping("/settlement")
    public SettlementView settlement(
            @PathVariable long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal user
    ) {
        return settlements.get(tripId, user.userId());
    }

    @PostMapping("/settlement/expenses")
    public ResponseEntity<SettlementView> addSharedExpense(
            @PathVariable long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal user,
            @Valid @RequestBody SharedExpenseRequest request
    ) {
        SettlementView result = settlements.create(tripId, user.userId(), request.command());
        return ResponseEntity.created(URI.create(
                "/api/v1/trips/" + tripId + "/settlement")).body(result);
    }

    @DeleteMapping("/settlement/expenses/{expenseId}")
    public SettlementView deleteSharedExpense(
            @PathVariable long tripId,
            @PathVariable long expenseId,
            @AuthenticationPrincipal RoutePlanPrincipal user
    ) {
        return settlements.delete(tripId, user.userId(), expenseId);
    }

    @GetMapping("/nearby-recommendations")
    public List<NearbyPlaceView> nearby(
            @PathVariable long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal user,
            @RequestParam LocalDate date,
            @RequestParam LocalTime currentTime,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal currentLatitude,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal currentLongitude,
            @RequestParam(required = false) @Positive Long nextPlaceId,
            @RequestParam(defaultValue = "90") @Min(15) @Max(720) int availableMinutes,
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int maxResults
    ) {
        return nearby.recommend(tripId, user.userId(), new NearbyQuery(
                date, currentTime, currentLatitude, currentLongitude,
                nextPlaceId, availableMinutes, maxResults));
    }

    public record AddMemberRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull TripMemberRole role
    ) {}

    public record UpdateMemberRequest(@NotNull TripMemberRole role) {}

    public record VoteRequest(@NotNull TripVoteValue value) {}

    public record SharedExpenseRequest(
            @NotNull UUID requestId,
            @NotNull LocalDate date,
            @NotNull Category category,
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("1") @DecimalMax("1000000000000") @Digits(integer = 13, fraction = 0)
            BigDecimal amountMinor,
            @Positive Long placeId,
            @NotNull BudgetCurrency currency,
            @NotNull @Positive Long payerUserId,
            @NotNull @Size(min = 1, max = 20) List<@NotNull @Positive Long> participantUserIds
    ) {
        CreateSharedExpense command() {
            return new CreateSharedExpense(
                    requestId, date, category, description, amountMinor.longValueExact(),
                    placeId, currency, payerUserId, participantUserIds);
        }
    }
}
