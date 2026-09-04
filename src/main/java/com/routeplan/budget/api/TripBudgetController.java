package com.routeplan.budget.api;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.budget.application.TripBudgetService;
import com.routeplan.budget.application.TripBudgetService.BudgetView;
import com.routeplan.budget.application.TripBudgetService.PlaceCostCommand;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.budget.domain.BudgetSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/budget")
public class TripBudgetController {

    private final TripBudgetService service;
    private final ResourceAccessService accessService;

    public TripBudgetController(TripBudgetService service, ResourceAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    @GetMapping
    public BudgetView get(@PathVariable Long tripId, @AuthenticationPrincipal RoutePlanPrincipal principal) {
        accessService.requireTripViewer(tripId, principal.userId());
        return service.get(tripId);
    }

    @PutMapping
    public BudgetView replace(
            @PathVariable Long tripId,
            @AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody BudgetRequest request
    ) {
        accessService.requireTripEditor(tripId, principal.userId());
        return service.replace(tripId,
                new BudgetSettings(request.currency(), toMinor(request.limitMinor()),
                        request.fixedCostMinor().longValueExact()),
                request.placeCosts().stream()
                        .map(cost -> new PlaceCostCommand(cost.placeId(), toMinor(cost.estimatedCostMinor()))).toList());
    }

    private static Long toMinor(BigDecimal amount) {
        return amount == null ? null : amount.longValueExact();
    }

    public record BudgetRequest(
            @NotNull BudgetCurrency currency,
            @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer = 13, fraction = 0) BigDecimal limitMinor,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer = 13, fraction = 0) BigDecimal fixedCostMinor,
            @NotNull @Size(max = 50) List<@NotNull @Valid PlaceCostRequest> placeCosts
    ) {}

    public record PlaceCostRequest(
            @NotNull @Min(1) Long placeId,
            @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer = 13, fraction = 0) BigDecimal estimatedCostMinor
    ) {}
}
