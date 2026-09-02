package com.routeplan.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalUsageMetricsTest {

    @Test
    void publishesDurableUnitsCallsCostRatioAndBudget() {
        ExternalUsageGuard usage = mock(ExternalUsageGuard.class);
        ExternalUsageProperties properties = new ExternalUsageProperties();
        properties.setGoogleMonthlyBudgetUsd(new BigDecimal("12.50"));
        when(usage.current()).thenReturn(List.of(new ExternalUsageGuard.Usage(
                "GOOGLE_ROUTES", "google", LocalDate.of(2026, 9, 1),
                90, 100, 10, 90.0, ExternalUsageGuard.UsageStatus.WARNING,
                4, 3, 1, 80, 10, 0,
                75.0, 120L, 250L,
                0, 0, null, null, null,
                new BigDecimal("0.450000"), true)));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExternalUsageMetrics metrics = new ExternalUsageMetrics(usage, properties, registry);

        metrics.refresh();

        assertThat(gauge(registry, "routeplan.external.usage.units", "operation", "GOOGLE_ROUTES"))
                .isEqualTo(90);
        assertThat(gauge(registry, "routeplan.external.usage.calls", "outcome", "success"))
                .isEqualTo(3);
        assertThat(gauge(registry, "routeplan.external.usage.cost.usd", "configured", "true"))
                .isEqualTo(0.45);
        assertThat(gauge(registry, "routeplan.external.usage.ratio", "provider", "google"))
                .isEqualTo(0.9);
        assertThat(gauge(registry, "routeplan.external.budget.usd", "provider", "google"))
                .isEqualTo(12.5);
    }

    private double gauge(SimpleMeterRegistry registry, String name, String tag, String value) {
        return registry.get(name).tag(tag, value).gauge().value();
    }
}
