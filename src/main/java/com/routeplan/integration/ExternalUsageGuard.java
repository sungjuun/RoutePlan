package com.routeplan.integration;

import com.routeplan.integration.google.*;
import com.routeplan.integration.retry.ExternalApiOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Counts attempted billable units, including retries; never claims to be a Google invoice. */
@Component
public class ExternalUsageGuard {
    private final JdbcTemplate jdbc;
    private final Map<String, Long> limits;

    public ExternalUsageGuard(JdbcTemplate jdbc,
            @Value("${routeplan.google.monthly-search-limit:4000}") long search,
            @Value("${routeplan.google.monthly-details-limit:900}") long details,
            @Value("${routeplan.google.monthly-matrix-limit:9000}") long matrix,
            @Value("${routeplan.google.monthly-geometry-limit:9000}") long geometry) {
        this.jdbc = jdbc;
        this.limits = Map.of("GOOGLE_PLACES", search, "GOOGLE_PLACE_DETAILS", details,
                "GOOGLE_ROUTES", matrix, "GOOGLE_GEOMETRY", geometry);
        if (limits.values().stream().anyMatch(value -> value < 0)) throw new IllegalArgumentException("호출 한도는 음수일 수 없습니다.");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(ExternalApiOperation operation, long units) {
        if (!limits.containsKey(operation.name())) return;
        long limit = limits.get(operation.name());
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        if (units < 1 || units > limit) throw limited();
        int changed = jdbc.update("""
                INSERT INTO external_api_usage(operation, usage_month, units) VALUES (?, ?, ?)
                ON CONFLICT (operation, usage_month) DO UPDATE
                SET units = external_api_usage.units + EXCLUDED.units
                WHERE external_api_usage.units + EXCLUDED.units <= ?
                """, operation.name(), month, units, limit);
        if (changed == 0) throw limited();
    }

    public List<Usage> current() {
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        Map<String, Long> used = new HashMap<>();
        jdbc.query("SELECT operation, units FROM external_api_usage WHERE usage_month = ?",
                rs -> { used.put(rs.getString(1), rs.getLong(2)); }, month);
        return limits.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new Usage(e.getKey(), month, used.getOrDefault(e.getKey(), 0L), e.getValue())).toList();
    }

    private ExternalProviderException limited() {
        return new ExternalProviderException(ExternalProviderFailure.RATE_LIMITED,
                "RoutePlan 월별 외부 API 호출 한도에 도달했습니다. Google 청구서와 별도의 앱 안전 한도입니다.");
    }
    public record Usage(String operation, LocalDate month, long attemptedUnits, long limit) {}
}
