package com.routeplan.budget.exchange;

import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "routeplan.exchange", name = "provider", havingValue = "DISABLED")
public class DisabledExchangeRateProvider implements ExchangeRateProvider {

    @Override
    public RateQuote latest(BudgetCurrency base, BudgetCurrency quote) {
        throw new ExternalProviderException(
                ExternalProviderFailure.NOT_CONFIGURED,
                "환율 자동 조회가 꺼져 있습니다. ROUTEPLAN_EXCHANGE_PROVIDER를 설정해 주세요."
        );
    }
}
