package com.routeplan.budget.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FrankfurterExchangeRateProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesMoneySafeDecimalQuote() throws Exception {
        Instant fetchedAt = Instant.parse("2026-09-03T00:00:00Z");
        var result = FrankfurterExchangeRateProvider.parse(objectMapper.readTree("""
                {"date":"2026-09-02","base":"JPY","quote":"KRW","rate":9.3817}
                """), fetchedAt);

        assertThat(result.base()).isEqualTo(BudgetCurrency.JPY);
        assertThat(result.quote()).isEqualTo(BudgetCurrency.KRW);
        assertThat(result.rate()).isEqualByComparingTo("9.3817");
        assertThat(result.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(result.provider()).isEqualTo("FRANKFURTER");
    }

    @Test
    void rejectsUnknownOrNonPositiveQuotes() {
        assertThatThrownBy(() -> FrankfurterExchangeRateProvider.parse(objectMapper.readTree("""
                {"date":"2026-09-02","base":"JPY","quote":"KRW","rate":0}
                """), Instant.now()))
                .isInstanceOf(ExternalProviderException.class)
                .extracting("failure").isEqualTo(ExternalProviderFailure.INVALID_RESPONSE);
    }
}
