package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable, fail-open L2 cache. PostGIS points are stored for spatial diagnostics and future proximity reuse. */
@Component
@ConditionalOnProperty(
        prefix = "routeplan.route.cache",
        name = "persistent-enabled",
        havingValue = "true"
)
public class PostgisRouteLegCache implements RouteLegCache {

    private static final Logger log = LoggerFactory.getLogger(PostgisRouteLegCache.class);
    private static final short CACHE_VERSION = 1;
    private static final String PROVIDER = "GOOGLE_ROUTES";
    private static final String UPSERT = """
            INSERT INTO route_leg_cache (
                cache_version, provider, transport_mode,
                origin_latitude_e6, origin_longitude_e6,
                destination_latitude_e6, destination_longitude_e6,
                departure_bucket, origin, destination,
                distance_meters, travel_minutes, expires_at, created_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (
                cache_version, provider, transport_mode,
                origin_latitude_e6, origin_longitude_e6,
                destination_latitude_e6, destination_longitude_e6, departure_bucket
            ) DO UPDATE SET
                distance_meters = EXCLUDED.distance_meters,
                travel_minutes = EXCLUDED.travel_minutes,
                expires_at = EXCLUDED.expires_at,
                updated_at = CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RouteCacheProperties properties;
    private final RouteCacheTierMetrics metrics;
    private final RouteCacheStampedeMetrics stampedeMetrics;

    public PostgisRouteLegCache(
            JdbcTemplate jdbcTemplate,
            RouteCacheProperties properties,
            RouteCacheTierMetrics metrics,
            RouteCacheStampedeMetrics stampedeMetrics
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.metrics = metrics;
        this.stampedeMetrics = stampedeMetrics;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public RouteCacheRead getAll(Set<RouteCacheKey> keys) {
        if (keys.isEmpty()) return RouteCacheRead.empty();
        List<RouteCacheKey> ordered = List.copyOf(keys);
        Map<RouteCacheKey, RouteResult> routes = new LinkedHashMap<>();
        int failures = 0;
        for (int start = 0; start < ordered.size(); start += properties.getDatabaseBatchSize()) {
            List<RouteCacheKey> batch = ordered.subList(
                    start, Math.min(start + properties.getDatabaseBatchSize(), ordered.size()));
            try {
                routes.putAll(readBatch(batch));
            } catch (DataAccessException exception) {
                failures++;
                log.warn("PostGIS Route Cache 읽기에 실패해 다음 계층으로 fallback합니다: {}",
                        exception.getClass().getSimpleName());
            }
        }
        metrics.read("database", routes.size(), keys.size() - routes.size(), failures);
        return new RouteCacheRead(routes, failures);
    }

    private Map<RouteCacheKey, RouteResult> readBatch(List<RouteCacheKey> keys) {
        String values = String.join(", ", java.util.Collections.nCopies(keys.size(),
                "(?::INTEGER, ?::VARCHAR, ?::INTEGER, ?::INTEGER, ?::INTEGER, ?::INTEGER, ?::TIMESTAMPTZ)"));
        String sql = """
                WITH requested(request_index, transport_mode, origin_latitude_e6, origin_longitude_e6,
                     destination_latitude_e6, destination_longitude_e6, departure_bucket) AS (
                    VALUES %s
                )
                SELECT requested.request_index, cache.distance_meters, cache.travel_minutes
                FROM requested
                JOIN route_leg_cache cache
                  ON cache.cache_version = %d
                 AND cache.provider = '%s'
                 AND cache.transport_mode = requested.transport_mode
                 AND cache.origin_latitude_e6 = requested.origin_latitude_e6
                 AND cache.origin_longitude_e6 = requested.origin_longitude_e6
                 AND cache.destination_latitude_e6 = requested.destination_latitude_e6
                 AND cache.destination_longitude_e6 = requested.destination_longitude_e6
                 AND cache.departure_bucket = requested.departure_bucket
                WHERE cache.expires_at > CURRENT_TIMESTAMP
                """.formatted(values, CACHE_VERSION, PROVIDER);
        return jdbcTemplate.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            int parameter = 1;
            for (int index = 0; index < keys.size(); index++) {
                RouteCacheKey key = keys.get(index);
                statement.setInt(parameter++, index);
                statement.setString(parameter++, key.transportMode().name());
                statement.setInt(parameter++, coordinate(key.origin().latitude()));
                statement.setInt(parameter++, coordinate(key.origin().longitude()));
                statement.setInt(parameter++, coordinate(key.destination().latitude()));
                statement.setInt(parameter++, coordinate(key.destination().longitude()));
                statement.setTimestamp(parameter++, Timestamp.from(bucket(key)));
            }
            return statement;
        }, resultSet -> {
            Map<RouteCacheKey, RouteResult> result = new LinkedHashMap<>();
            while (resultSet.next()) {
                RouteCacheKey key = keys.get(resultSet.getInt("request_index"));
                result.put(key, new RouteResult(
                        resultSet.getLong("distance_meters"),
                        resultSet.getInt("travel_minutes")));
            }
            return result;
        });
    }

    @Override
    public int putAll(Map<RouteCacheKey, RouteResult> routes) {
        if (routes.isEmpty()) return 0;
        List<Map.Entry<RouteCacheKey, RouteResult>> entries = new ArrayList<>(routes.entrySet());
        int failures = 0;
        for (int start = 0; start < entries.size(); start += properties.getDatabaseBatchSize()) {
            List<Map.Entry<RouteCacheKey, RouteResult>> batch = entries.subList(
                    start, Math.min(start + properties.getDatabaseBatchSize(), entries.size()));
            try {
                writeBatch(batch);
            } catch (DataAccessException exception) {
                failures++;
                log.warn("PostGIS Route Cache 저장에 실패했지만 계산 결과는 유지합니다: {}",
                        exception.getClass().getSimpleName());
            }
        }
        metrics.write("database", routes.size(), failures);
        return failures;
    }

    private void writeBatch(List<Map.Entry<RouteCacheKey, RouteResult>> entries) {
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws java.sql.SQLException {
                RouteCacheKey key = entries.get(index).getKey();
                RouteResult route = entries.get(index).getValue();
                statement.setShort(1, CACHE_VERSION);
                statement.setString(2, PROVIDER);
                statement.setString(3, key.transportMode().name());
                statement.setInt(4, coordinate(key.origin().latitude()));
                statement.setInt(5, coordinate(key.origin().longitude()));
                statement.setInt(6, coordinate(key.destination().latitude()));
                statement.setInt(7, coordinate(key.destination().longitude()));
                statement.setTimestamp(8, Timestamp.from(bucket(key)));
                statement.setDouble(9, key.origin().longitude());
                statement.setDouble(10, key.origin().latitude());
                statement.setDouble(11, key.destination().longitude());
                statement.setDouble(12, key.destination().latitude());
                statement.setLong(13, route.distanceMeters());
                statement.setInt(14, route.estimatedTravelMinutes());
                statement.setTimestamp(15, Timestamp.from(now.plus(ttl(key.transportMode()))));
            }

            @Override
            public int getBatchSize() {
                return entries.size();
            }
        });
    }

    @Override
    public RouteCacheLease acquireRefreshLock(Set<RouteCacheKey> keys) {
        if (keys.isEmpty()) return RouteCacheLease.bypass();
        String lockKey = lockKey(keys);
        UUID token = UUID.randomUUID();
        try {
            List<UUID> owners = jdbcTemplate.query("""
                    INSERT INTO route_cache_refresh_locks(lock_key, owner_token, expires_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (lock_key) DO UPDATE SET
                        owner_token = EXCLUDED.owner_token,
                        expires_at = EXCLUDED.expires_at
                    WHERE route_cache_refresh_locks.expires_at <= CURRENT_TIMESTAMP
                    RETURNING owner_token
                    """, statement -> {
                statement.setString(1, lockKey);
                statement.setObject(2, token);
                statement.setTimestamp(3, Timestamp.from(Instant.now().plus(properties.getRefreshLockTtl())));
            }, (resultSet, row) -> resultSet.getObject(1, UUID.class));
            if (!owners.isEmpty() && token.equals(owners.getFirst())) {
                stampedeMetrics.lock("database_acquired");
                return RouteCacheLease.acquired(() -> release(lockKey, token));
            }
            stampedeMetrics.lock("database_contended");
            return RouteCacheLease.waiting();
        } catch (DataAccessException exception) {
            stampedeMetrics.lock("database_bypass");
            log.warn("PostGIS Route Cache 갱신 잠금을 사용할 수 없어 잠금 없이 진행합니다: {}",
                    exception.getClass().getSimpleName());
            return RouteCacheLease.bypass();
        }
    }

    @Override
    public RouteCacheRead waitForRefresh(Set<RouteCacheKey> keys) {
        long started = System.nanoTime();
        long deadline = started + properties.getRefreshWait().toNanos();
        RouteCacheRead latest = RouteCacheRead.empty();
        while (System.nanoTime() < deadline) {
            latest = getAll(keys);
            if (latest.routes().size() == keys.size() || latest.failureCount() > 0) break;
            try {
                Thread.sleep(properties.getRefreshPollInterval().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        stampedeMetrics.waitCompleted("database", Math.max(0, (System.nanoTime() - started) / 1_000_000));
        return latest.routes().size() == keys.size() ? latest : getAll(keys);
    }

    @Scheduled(
            fixedDelayString = "${routeplan.route.cache.cleanup-interval:10m}",
            initialDelayString = "${routeplan.route.cache.cleanup-interval:10m}"
    )
    public void evictExpired() {
        try {
            int removed = jdbcTemplate.update("""
                    DELETE FROM route_leg_cache
                    WHERE id IN (
                        SELECT id FROM route_leg_cache
                        WHERE expires_at <= CURRENT_TIMESTAMP
                        ORDER BY expires_at, id
                        LIMIT ?
                    )
                    """, properties.getCleanupBatchSize());
            jdbcTemplate.update("DELETE FROM route_cache_refresh_locks WHERE expires_at <= CURRENT_TIMESTAMP");
            if (removed > 0) metrics.cleanup("removed", removed);
        } catch (DataAccessException exception) {
            metrics.cleanup("failure", 0);
            log.warn("PostGIS Route Cache 만료 데이터 정리에 실패했습니다: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private void release(String lockKey, UUID token) {
        try {
            jdbcTemplate.update(
                    "DELETE FROM route_cache_refresh_locks WHERE lock_key = ? AND owner_token = ?",
                    lockKey, token);
        } catch (DataAccessException exception) {
            log.warn("PostGIS Route Cache 갱신 잠금 해제에 실패했습니다: {}",
                    exception.getClass().getSimpleName());
        }
    }

    private String lockKey(Set<RouteCacheKey> keys) {
        String material = keys.stream()
                .map(this::canonicalKey)
                .sorted()
                .reduce("", (left, right) -> left + "\n" + right);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String canonicalKey(RouteCacheKey key) {
        return key.transportMode().name() + ':' + bucket(key).getEpochSecond()
                + ':' + coordinate(key.origin().latitude()) + ':' + coordinate(key.origin().longitude())
                + ':' + coordinate(key.destination().latitude()) + ':' + coordinate(key.destination().longitude());
    }

    private Instant bucket(RouteCacheKey key) {
        return key.departureBucket(properties.getDepartureBucket());
    }

    private int coordinate(double coordinate) {
        return BigDecimal.valueOf(coordinate)
                .movePointRight(6)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private Duration ttl(TransportMode mode) {
        return switch (mode) {
            case WALKING -> properties.getWalkingTtl();
            case DRIVING -> properties.getDrivingTtl();
            case PUBLIC_TRANSIT -> properties.getTransitTtl();
        };
    }
}
