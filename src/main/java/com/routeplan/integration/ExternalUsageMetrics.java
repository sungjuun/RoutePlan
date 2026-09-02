package com.routeplan.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes durable monthly provider usage without summing duplicate values across backend replicas. */
@Component
public class ExternalUsageMetrics {

    private static final Logger log = LoggerFactory.getLogger(ExternalUsageMetrics.class);

    private final ExternalUsageGuard usage;
    private final MultiGauge units;
    private final MultiGauge calls;
    private final MultiGauge cost;
    private final MultiGauge ratio;

    public ExternalUsageMetrics(
            ExternalUsageGuard usage,
            ExternalUsageProperties properties,
            MeterRegistry registry
    ) {
        this.usage = usage;
        this.units = MultiGauge.builder("routeplan.external.usage.units")
                .description("Durable current-month provider billable units")
                .register(registry);
        this.calls = MultiGauge.builder("routeplan.external.usage.calls")
                .description("Durable current-month provider call outcomes")
                .register(registry);
        this.cost = MultiGauge.builder("routeplan.external.usage.cost.usd")
                .description("Configured-price estimate for current-month provider usage")
                .register(registry);
        this.ratio = MultiGauge.builder("routeplan.external.usage.ratio")
                .description("Current-month app safety-limit utilization ratio")
                .register(registry);
        Gauge.builder("routeplan.external.budget.usd", properties,
                        value -> value.getGoogleMonthlyBudgetUsd().doubleValue())
                .tag("provider", "google")
                .description("Configured monthly provider budget in USD")
                .register(registry);
        Gauge.builder("routeplan.external.budget.usd", properties,
                        value -> value.getOpenAiMonthlyBudgetUsd().doubleValue())
                .tag("provider", "openai")
                .description("Configured monthly provider budget in USD")
                .register(registry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    @Scheduled(
            fixedDelayString = "${routeplan.external.usage.metrics-refresh-interval:1m}",
            initialDelayString = "${routeplan.external.usage.metrics-refresh-interval:1m}"
    )
    public void refresh() {
        try {
            List<ExternalUsageGuard.Usage> current = usage.current();
            units.register(current.stream().map(value -> MultiGauge.Row.of(tags(value),
                    value.attemptedUnits())).toList(), true);
            List<MultiGauge.Row<?>> callRows = new ArrayList<>();
            current.forEach(value -> {
                callRows.add(MultiGauge.Row.of(tags(value).and("outcome", "attempt"), value.attemptCount()));
                callRows.add(MultiGauge.Row.of(tags(value).and("outcome", "success"), value.successCount()));
                callRows.add(MultiGauge.Row.of(tags(value).and("outcome", "failure"), value.failureCount()));
            });
            calls.register(callRows, true);
            cost.register(current.stream().map(value -> MultiGauge.Row.of(
                    tags(value).and("configured", Boolean.toString(value.costConfigured())),
                    value.estimatedCostUsd())).toList(), true);
            ratio.register(current.stream().map(value -> MultiGauge.Row.of(tags(value),
                    value.usagePercent() / 100.0)).toList(), true);
        } catch (RuntimeException exception) {
            log.warn("외부 API 장기 사용량 메트릭 갱신에 실패했습니다: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private Tags tags(ExternalUsageGuard.Usage value) {
        return Tags.of("provider", value.provider(), "operation", value.operation());
    }
}
