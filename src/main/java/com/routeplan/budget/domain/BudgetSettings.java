package com.routeplan.budget.domain;

import java.util.Objects;

/** Amounts use integer minor units: KRW/JPY units, or cents for the other currencies. */
public record BudgetSettings(BudgetCurrency currency, Long limitMinor, long fixedCostMinor) {

    public static final long MAX_AMOUNT = 1_000_000_000_000L;

    public BudgetSettings {
        Objects.requireNonNull(currency, "예산 통화는 필수입니다.");
        requireAmount(limitMinor);
        requireAmount(fixedCostMinor);
    }

    public static void requireAmount(Long amount) {
        if (amount != null && (amount < 0 || amount > MAX_AMOUNT)) {
            throw new IllegalArgumentException("금액은 0 이상 1,000,000,000,000 최소 통화 단위 이하여야 합니다.");
        }
    }
}
