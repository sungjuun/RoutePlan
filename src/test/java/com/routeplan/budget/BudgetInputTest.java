package com.routeplan.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.budget.application.BudgetInput;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.optimization.constraint.BudgetConstraintException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BudgetInputTest {

    @Test
    void subtractsFixedAndCompletedCostsOnce() {
        BudgetInput input = new BudgetInput(new BudgetSettings(BudgetCurrency.USD, 10_000L, 2_000L), Map.of());
        assertThat(input.remaining(3_000L, false).availableMinor()).isEqualTo(5_000L);
        assertThat(input.remaining(8_000L, false).availableMinor()).isZero();
    }

    @Test
    void rejectsSpentBudgetAndUnpricedCompletedVisits() {
        BudgetInput input = new BudgetInput(new BudgetSettings(BudgetCurrency.KRW, 100L, 20L), Map.of());
        assertThatThrownBy(() -> input.remaining(81L, false)).isInstanceOf(BudgetConstraintException.class);
        assertThatThrownBy(() -> input.remaining(0L, true)).isInstanceOf(BudgetConstraintException.class);
        BudgetInput unlimited = new BudgetInput(new BudgetSettings(BudgetCurrency.KRW, null, 20L), Map.of());
        assertThat(unlimited.remaining(0L, true).availableMinor()).isNull();
    }

    @Test
    void rejectsNegativeAndUnsafeAmountsButAllowsUnknownAndZero() {
        assertThatThrownBy(() -> new BudgetSettings(BudgetCurrency.KRW, -1L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BudgetSettings.requireAmount(BudgetSettings.MAX_AMOUNT + 1)).isInstanceOf(IllegalArgumentException.class);
        BudgetSettings.requireAmount(null);
        BudgetSettings.requireAmount(0L);
    }
}
