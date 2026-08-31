package com.routeplan.integration;

import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.retry.ExternalApiOperation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable app-side safety limits and telemetry. Provider billing remains authoritative. */
@Component
public class ExternalUsageGuard {
    private final JdbcTemplate jdbc;
    private final ExternalUsageProperties properties;
    private final Map<ExternalApiOperation, Long> limits;
    private final Map<ExternalApiOperation, BigDecimal> googlePrices;

    public ExternalUsageGuard(
            JdbcTemplate jdbc,
            ExternalUsageProperties properties,
            @Value("${routeplan.google.monthly-search-limit:4000}") long search,
            @Value("${routeplan.google.monthly-details-limit:900}") long details,
            @Value("${routeplan.google.monthly-matrix-limit:9000}") long matrix,
            @Value("${routeplan.google.monthly-geometry-limit:9000}") long geometry
    ) {
        this.jdbc = jdbc;
        this.properties = properties;
        properties.validate();
        this.limits = new EnumMap<>(ExternalApiOperation.class);
        limits.put(ExternalApiOperation.GOOGLE_PLACES, search);
        limits.put(ExternalApiOperation.GOOGLE_PLACE_DETAILS, details);
        limits.put(ExternalApiOperation.GOOGLE_ROUTES, matrix);
        limits.put(ExternalApiOperation.GOOGLE_GEOMETRY, geometry);
        limits.put(ExternalApiOperation.OPENAI_RESPONSES, properties.getOpenAiMonthlyRequestLimit());
        if (limits.values().stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("호출 한도는 음수일 수 없습니다.");
        }
        this.googlePrices = new EnumMap<>(ExternalApiOperation.class);
        googlePrices.put(ExternalApiOperation.GOOGLE_PLACES, properties.getGooglePlacesUsdPerThousand());
        googlePrices.put(ExternalApiOperation.GOOGLE_PLACE_DETAILS, properties.getGooglePlaceDetailsUsdPerThousand());
        googlePrices.put(ExternalApiOperation.GOOGLE_ROUTES, properties.getGoogleRoutesUsdPerThousand());
        googlePrices.put(ExternalApiOperation.GOOGLE_GEOMETRY, properties.getGoogleGeometryUsdPerThousand());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(ExternalApiOperation operation, long units) {
        Long limit = limits.get(operation);
        if (limit == null) return;
        if (units < 1 || units > limit) throw limited();
        if (operation == ExternalApiOperation.OPENAI_RESPONSES && properties.getOpenAiMonthlyTokenLimit() == 0) {
            throw limited();
        }
        LocalDate month = currentMonth();
        int changed;
        if (operation == ExternalApiOperation.OPENAI_RESPONSES) {
            changed = jdbc.update("""
                    INSERT INTO external_api_usage(operation, usage_month, units, attempt_count)
                    VALUES (?, ?, ?, 1)
                    ON CONFLICT (operation, usage_month) DO UPDATE
                    SET units = external_api_usage.units + EXCLUDED.units,
                        attempt_count = external_api_usage.attempt_count + 1
                    WHERE external_api_usage.units + EXCLUDED.units <= ?
                      AND external_api_usage.input_tokens + external_api_usage.output_tokens < ?
                    """, operation.name(), month, units, limit, properties.getOpenAiMonthlyTokenLimit());
        } else {
            changed = jdbc.update("""
                    INSERT INTO external_api_usage(operation, usage_month, units, attempt_count)
                    VALUES (?, ?, ?, 1)
                    ON CONFLICT (operation, usage_month) DO UPDATE
                    SET units = external_api_usage.units + EXCLUDED.units,
                        attempt_count = external_api_usage.attempt_count + 1
                    WHERE external_api_usage.units + EXCLUDED.units <= ?
                    """, operation.name(), month, units, limit);
        }
        if (changed == 0) throw limited();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(ExternalApiOperation operation, long units, boolean success, long latencyMs) {
        if (!limits.containsKey(operation)) return;
        jdbc.update("""
                UPDATE external_api_usage
                SET success_count = success_count + ?,
                    failure_count = failure_count + ?,
                    successful_units = successful_units + ?,
                    failed_units = failed_units + ?,
                    total_latency_ms = total_latency_ms + ?,
                    max_latency_ms = GREATEST(max_latency_ms, ?)
                WHERE operation = ? AND usage_month = ?
                """, success ? 1 : 0, success ? 0 : 1,
                success ? units : 0, success ? 0 : units,
                Math.max(0, latencyMs), Math.max(0, latencyMs), operation.name(), currentMonth());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOpenAiTokens(long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) return;
        jdbc.update("""
                UPDATE external_api_usage
                SET input_tokens = input_tokens + ?, output_tokens = output_tokens + ?
                WHERE operation = ? AND usage_month = ?
                """, inputTokens, outputTokens, ExternalApiOperation.OPENAI_RESPONSES.name(), currentMonth());
    }

    public List<Usage> current() {
        LocalDate month = currentMonth();
        Map<ExternalApiOperation, Counters> counters = new EnumMap<>(ExternalApiOperation.class);
        jdbc.query("""
                        SELECT operation, units, attempt_count, success_count, failure_count,
                               successful_units, failed_units, total_latency_ms, max_latency_ms,
                               input_tokens, output_tokens
                        FROM external_api_usage WHERE usage_month = ?
                        """,
                rs -> {
                    try {
                        counters.put(ExternalApiOperation.valueOf(rs.getString("operation")), new Counters(
                                rs.getLong("units"), rs.getLong("attempt_count"),
                                rs.getLong("success_count"), rs.getLong("failure_count"),
                                rs.getLong("successful_units"), rs.getLong("failed_units"),
                                rs.getLong("total_latency_ms"), rs.getLong("max_latency_ms"),
                                rs.getLong("input_tokens"), rs.getLong("output_tokens")));
                    } catch (IllegalArgumentException ignored) {
                        // A newer operation written by another app version must not break this dashboard.
                    }
                }, month);
        return limits.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> usage(entry.getKey(), month, entry.getValue(),
                        counters.getOrDefault(entry.getKey(), Counters.EMPTY)))
                .toList();
    }

    private Usage usage(ExternalApiOperation operation, LocalDate month, long limit, Counters value) {
        long remaining = Math.max(0, limit - value.units());
        double percent = percentage(value.units(), limit);
        long completed = value.successCount() + value.failureCount();
        Double successRate = completed == 0 ? null : percentage(value.successCount(), completed);
        Long averageLatency = completed == 0 ? null : value.totalLatencyMs() / completed;
        Long tokenLimit = operation == ExternalApiOperation.OPENAI_RESPONSES
                ? properties.getOpenAiMonthlyTokenLimit() : null;
        long tokens = value.inputTokens() + value.outputTokens();
        Long remainingTokens = tokenLimit == null ? null : Math.max(0, tokenLimit - tokens);
        Double tokenPercent = tokenLimit == null ? null : percentage(tokens, tokenLimit);
        UsageStatus status = status(percent, tokenPercent);
        Cost cost = estimatedCost(operation, value);
        return new Usage(operation.name(), operation.provider(), month, value.units(), limit, remaining,
                percent, status, value.attemptCount(), value.successCount(), value.failureCount(),
                value.successfulUnits(), value.failedUnits(),
                Math.max(0, value.units() - value.successfulUnits() - value.failedUnits()),
                successRate, averageLatency, value.maxLatencyMs(), value.inputTokens(), value.outputTokens(),
                tokenLimit, remainingTokens, tokenPercent, cost.amount(), cost.configured());
    }

    private UsageStatus status(double requestPercent, Double tokenPercent) {
        double highest = tokenPercent == null ? requestPercent : Math.max(requestPercent, tokenPercent);
        if (highest >= 100) return UsageStatus.BLOCKED;
        if (highest >= properties.getWarningPercent()) return UsageStatus.WARNING;
        return UsageStatus.NORMAL;
    }

    private Cost estimatedCost(ExternalApiOperation operation, Counters value) {
        if (operation == ExternalApiOperation.OPENAI_RESPONSES) {
            BigDecimal input = properties.getOpenAiInputUsdPerMillion();
            BigDecimal output = properties.getOpenAiOutputUsdPerMillion();
            boolean configured = input.signum() > 0 || output.signum() > 0;
            BigDecimal amount = input.multiply(BigDecimal.valueOf(value.inputTokens()))
                    .add(output.multiply(BigDecimal.valueOf(value.outputTokens())))
                    .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
            return new Cost(amount, configured);
        }
        BigDecimal price = googlePrices.getOrDefault(operation, BigDecimal.ZERO);
        return new Cost(price.multiply(BigDecimal.valueOf(value.units()))
                .divide(BigDecimal.valueOf(1_000), 6, RoundingMode.HALF_UP), price.signum() > 0);
    }

    private double percentage(long used, long limit) {
        if (limit == 0) return 100;
        return BigDecimal.valueOf(used).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(limit), 1, RoundingMode.HALF_UP).doubleValue();
    }

    private LocalDate currentMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    }

    private ExternalProviderException limited() {
        return new ExternalProviderException(ExternalProviderFailure.RATE_LIMITED,
                "RoutePlan 월별 외부 API 안전 한도에 도달했습니다. 공급자 청구·쿼터와 별도의 앱 한도입니다.");
    }

    public enum UsageStatus { NORMAL, WARNING, BLOCKED }

    public record Usage(
            String operation, String provider, LocalDate month,
            long attemptedUnits, long limit, long remainingUnits, double usagePercent, UsageStatus status,
            long attemptCount, long successCount, long failureCount,
            long successfulUnits, long failedUnits, long unclassifiedUnits,
            Double successRatePercent, Long averageLatencyMs, long maxLatencyMs,
            long inputTokens, long outputTokens, Long tokenLimit, Long remainingTokens, Double tokenUsagePercent,
            BigDecimal estimatedCostUsd, boolean costConfigured
    ) {}

    private record Counters(long units, long attemptCount, long successCount, long failureCount,
            long successfulUnits, long failedUnits, long totalLatencyMs, long maxLatencyMs,
            long inputTokens, long outputTokens) {
        private static final Counters EMPTY = new Counters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private record Cost(BigDecimal amount, boolean configured) {}
}
