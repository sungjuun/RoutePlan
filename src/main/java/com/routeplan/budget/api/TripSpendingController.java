package com.routeplan.budget.api;

import com.routeplan.auth.*;
import com.routeplan.budget.application.TripSpendingService;
import com.routeplan.budget.application.TripSpendingService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/spending")
public class TripSpendingController {
    private final TripSpendingService service;
    private final ResourceAccessService access;
    public TripSpendingController(TripSpendingService service, ResourceAccessService access) { this.service = service; this.access = access; }
    @GetMapping
    public Spending get(@PathVariable long tripId, @AuthenticationPrincipal RoutePlanPrincipal user) {
        access.requireTripOwner(tripId, user.userId()); return service.get(tripId);
    }
    @PutMapping("/allocations")
    public Spending limits(@PathVariable long tripId, @AuthenticationPrincipal RoutePlanPrincipal user, @Valid @RequestBody Limits body) {
        access.requireTripOwner(tripId, user.userId());
        return service.allocations(tripId, body.allocations().stream().map(v -> new Allocation(v.date(), v.category(), v.limitMinor().longValueExact())).toList(), body.currency());
    }
    @PostMapping("/expenses")
    public Spending create(@PathVariable long tripId, @AuthenticationPrincipal RoutePlanPrincipal user, @Valid @RequestBody ExpenseRequest body) {
        access.requireTripOwner(tripId, user.userId()); return save(tripId, null, body);
    }
    @PutMapping("/expenses/{expenseId}")
    public Spending update(@PathVariable long tripId, @PathVariable long expenseId, @AuthenticationPrincipal RoutePlanPrincipal user, @Valid @RequestBody ExpenseRequest body) {
        access.requireTripOwner(tripId, user.userId()); return save(tripId, expenseId, body);
    }
    @DeleteMapping("/expenses/{expenseId}")
    public Spending delete(@PathVariable long tripId, @PathVariable long expenseId, @AuthenticationPrincipal RoutePlanPrincipal user) {
        access.requireTripOwner(tripId, user.userId()); return service.delete(tripId, expenseId);
    }
    private Spending save(long tripId, Long id, ExpenseRequest b) { return service.save(tripId,id,b.requestId(),b.date(),b.category(),b.description(),b.amountMinor().longValueExact(), b.placeId(), b.currency()); }
    public record Limits(@NotNull com.routeplan.budget.domain.BudgetCurrency currency, @NotNull @Size(max=100) List<@NotNull @Valid LimitRequest> allocations) {}
    public record LimitRequest(LocalDate date, Category category,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer=13,fraction=0) BigDecimal limitMinor) {}
    public record ExpenseRequest(@NotNull com.routeplan.budget.domain.BudgetCurrency currency, @NotNull UUID requestId, @NotNull LocalDate date, @NotNull Category category,
            @NotBlank @Size(max=200) String description,
            @NotNull @DecimalMin("0") @DecimalMax("1000000000000") @Digits(integer=13,fraction=0) BigDecimal amountMinor,
            @Positive Long placeId) {}
}
