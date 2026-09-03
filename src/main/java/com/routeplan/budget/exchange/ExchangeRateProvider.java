package com.routeplan.budget.exchange;

import com.routeplan.budget.domain.BudgetCurrency;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public interface ExchangeRateProvider {

    RateQuote latest(BudgetCurrency base, BudgetCurrency quote);

    record RateQuote(
            BudgetCurrency base,
            BudgetCurrency quote,
            BigDecimal rate,
            LocalDate rateDate,
            Instant fetchedAt,
            String provider
    ) {
        public RateQuote {
            if (base == null || quote == null || rate == null || rate.signum() <= 0
                    || rateDate == null || fetchedAt == null || provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("환율 응답이 올바르지 않습니다.");
            }
        }
    }
}
