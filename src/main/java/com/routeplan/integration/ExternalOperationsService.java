package com.routeplan.integration;

import com.routeplan.integration.resilience.ExternalProviderGuard;
import com.routeplan.integration.resilience.ExternalResilienceProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExternalOperationsService {

    private final ExternalUsageGuard usageGuard;
    private final ExternalUsageProperties usageProperties;
    private final ExternalProviderGuard providerGuard;
    private final ExternalResilienceProperties resilienceProperties;

    public ExternalOperationsService(
            ExternalUsageGuard usageGuard,
            ExternalUsageProperties usageProperties,
            ExternalProviderGuard providerGuard,
            ExternalResilienceProperties resilienceProperties
    ) {
        this.usageGuard = usageGuard;
        this.usageProperties = usageProperties;
        this.providerGuard = providerGuard;
        this.resilienceProperties = resilienceProperties;
    }

    public Snapshot current() {
        List<ExternalUsageGuard.Usage> usage = usageGuard.current();
        List<ExternalProviderGuard.ProviderStatus> providers = providerGuard.current();
        List<Alert> alerts = new ArrayList<>();
        usage.forEach(row -> addUsageAlerts(row, alerts));
        providers.forEach(row -> addProviderAlerts(row, alerts));
        List<ProviderCost> costs = providerCosts(usage, alerts);
        return new Snapshot(usage, providers, costs, List.copyOf(alerts));
    }

    private void addUsageAlerts(ExternalUsageGuard.Usage row, List<Alert> alerts) {
        if (row.status() != ExternalUsageGuard.UsageStatus.NORMAL) {
            AlertSeverity severity = row.status() == ExternalUsageGuard.UsageStatus.BLOCKED
                    ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
            alerts.add(new Alert("USAGE_" + row.operation(), severity, row.provider(), row.operation(),
                    row.status() == ExternalUsageGuard.UsageStatus.BLOCKED
                            ? "앱 월간 안전 한도에 도달해 새 호출이 차단됩니다."
                            : "앱 월간 안전 한도의 " + row.usagePercent() + "%를 사용했습니다."));
        }
        long completed = row.successCount() + row.failureCount();
        if (completed >= resilienceProperties.getFailureRateMinimumCalls()) {
            double failurePercent = BigDecimal.valueOf(row.failureCount())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(completed), 1, RoundingMode.HALF_UP)
                    .doubleValue();
            if (failurePercent >= resilienceProperties.getFailureRateWarningPercent()) {
                alerts.add(new Alert("FAILURE_RATE_" + row.operation(), AlertSeverity.WARNING,
                        row.provider(), row.operation(), "이번 달 완료 호출 실패율이 " + failurePercent + "%입니다."));
            }
        }
    }

    private void addProviderAlerts(ExternalProviderGuard.ProviderStatus row, List<Alert> alerts) {
        if (row.state() != ExternalProviderGuard.CircuitState.CLOSED) {
            alerts.add(new Alert("CIRCUIT_" + row.provider(), AlertSeverity.CRITICAL,
                    row.provider(), null, row.state() == ExternalProviderGuard.CircuitState.OPEN
                    ? "연속 장애로 Circuit Breaker가 열려 있습니다. 자동 복구 시각: " + row.openUntil()
                    : "Circuit Breaker가 복구 확인을 위한 단일 시험 호출을 기다리고 있습니다."));
        } else if (row.activeCalls() >= row.maxConcurrentCalls()) {
            alerts.add(new Alert("BULKHEAD_" + row.provider(), AlertSeverity.WARNING,
                    row.provider(), null, "공급자별 동시 호출 한도를 모두 사용 중입니다."));
        }
    }

    private List<ProviderCost> providerCosts(
            List<ExternalUsageGuard.Usage> usage,
            List<Alert> alerts
    ) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Boolean> configured = new LinkedHashMap<>();
        usage.forEach(row -> {
            totals.merge(row.provider(), row.estimatedCostUsd(), BigDecimal::add);
            configured.merge(row.provider(), row.costConfigured(), Boolean::logicalOr);
        });
        return totals.entrySet().stream().map(entry -> {
            String provider = entry.getKey();
            BigDecimal budget = budget(provider);
            BigDecimal amount = entry.getValue().setScale(6, RoundingMode.HALF_UP);
            Double percent = budget.signum() == 0 ? null : amount.multiply(BigDecimal.valueOf(100))
                    .divide(budget, 1, RoundingMode.HALF_UP).doubleValue();
            if (percent != null && percent >= usageProperties.getWarningPercent()) {
                alerts.add(new Alert("COST_" + provider,
                        percent >= 100 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                        provider, null, "설정 단가 기준 월 추정 비용이 예산의 " + percent + "%입니다."));
            }
            return new ProviderCost(provider, amount, budget, percent,
                    configured.getOrDefault(provider, false));
        }).toList();
    }

    private BigDecimal budget(String provider) {
        return switch (provider) {
            case "google" -> usageProperties.getGoogleMonthlyBudgetUsd();
            case "openai" -> usageProperties.getOpenAiMonthlyBudgetUsd();
            default -> BigDecimal.ZERO;
        };
    }

    public record Snapshot(
            List<ExternalUsageGuard.Usage> usage,
            List<ExternalProviderGuard.ProviderStatus> providers,
            List<ProviderCost> costs,
            List<Alert> alerts
    ) {}

    public record ProviderCost(
            String provider,
            BigDecimal estimatedCostUsd,
            BigDecimal monthlyBudgetUsd,
            Double budgetUsagePercent,
            boolean costConfigured
    ) {}

    public record Alert(
            String code,
            AlertSeverity severity,
            String provider,
            String operation,
            String message
    ) {}

    public enum AlertSeverity { WARNING, CRITICAL }
}
