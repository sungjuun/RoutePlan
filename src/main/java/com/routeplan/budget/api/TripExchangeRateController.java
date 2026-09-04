package com.routeplan.budget.api;

import com.routeplan.auth.ResourceAccessService;
import com.routeplan.auth.RoutePlanPrincipal;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.budget.exchange.ExchangeRateProvider;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/exchange-rate")
public class TripExchangeRateController {

    private final ExchangeRateProvider provider;
    private final TripRepository tripRepository;
    private final ResourceAccessService accessService;

    public TripExchangeRateController(
            ExchangeRateProvider provider,
            TripRepository tripRepository,
            ResourceAccessService accessService
    ) {
        this.provider = provider;
        this.tripRepository = tripRepository;
        this.accessService = accessService;
    }

    @GetMapping
    public ExchangeRateView latest(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "KRW") BudgetCurrency quote,
            @AuthenticationPrincipal RoutePlanPrincipal principal
    ) {
        accessService.requireTripViewer(tripId, principal.userId());
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        ExchangeRateProvider.RateQuote result = provider.latest(trip.getBudgetSettings().currency(), quote);
        return new ExchangeRateView(result.base(), result.quote(), result.rate(), result.rateDate(),
                result.fetchedAt(), result.provider());
    }

    public record ExchangeRateView(
            BudgetCurrency base,
            BudgetCurrency quote,
            BigDecimal rate,
            LocalDate rateDate,
            Instant fetchedAt,
            String provider
    ) {}
}
