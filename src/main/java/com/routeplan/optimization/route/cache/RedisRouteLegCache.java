package com.routeplan.optimization.route.cache;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.route.cache",
        name = "enabled",
        havingValue = "true"
)
public class RedisRouteLegCache implements RouteLegCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRouteLegCache.class);

    private final StringRedisTemplate redisTemplate;
    private final RouteCacheProperties properties;
    private final RouteCacheStampedeMetrics stampedeMetrics;
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    @org.springframework.beans.factory.annotation.Autowired
    public RedisRouteLegCache(
            StringRedisTemplate redisTemplate,
            RouteCacheProperties properties,
            RouteCacheStampedeMetrics stampedeMetrics
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.stampedeMetrics = stampedeMetrics;
    }

    public RedisRouteLegCache(StringRedisTemplate redisTemplate, RouteCacheProperties properties) {
        this(redisTemplate, properties, null);
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public RouteCacheRead getAll(Set<RouteCacheKey> keys) {
        if (keys.isEmpty()) {
            return RouteCacheRead.empty();
        }
        List<RouteCacheKey> orderedKeys = List.copyOf(keys);
        List<String> redisKeys = orderedKeys.stream().map(this::redisKey).toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(redisKeys);
            if (values == null || values.size() != orderedKeys.size()) {
                log.warn("Route Cache MGET 응답 크기가 올바르지 않아 외부 Provider로 fallback합니다.");
                return new RouteCacheRead(Map.of(), 1);
            }
            Map<RouteCacheKey, RouteResult> hits = new LinkedHashMap<>();
            int failures = 0;
            for (int index = 0; index < orderedKeys.size(); index++) {
                String value = values.get(index);
                if (value == null) {
                    continue;
                }
                try {
                    hits.put(orderedKeys.get(index), decode(value));
                } catch (IllegalArgumentException exception) {
                    failures++;
                }
            }
            return new RouteCacheRead(hits, failures);
        } catch (RuntimeException exception) {
            log.warn(
                    "Route Cache 읽기에 실패해 외부 Provider로 fallback합니다: {}",
                    exception.getClass().getSimpleName()
            );
            return new RouteCacheRead(Map.of(), 1);
        }
    }

    @Override
    public int putAll(Map<RouteCacheKey, RouteResult> routes) {
        if (routes.isEmpty()) {
            return 0;
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                routes.forEach((key, route) -> connection.stringCommands().set(
                        serialize(serializer, redisKey(key)),
                        serialize(serializer, encode(route)),
                        SetCondition.upsert(),
                        Expiration.from(ttl(key.transportMode()))
                ));
                return null;
            });
            return 0;
        } catch (RuntimeException exception) {
            log.warn(
                    "Route Cache 저장에 실패했지만 계산 결과는 유지합니다: {}",
                    exception.getClass().getSimpleName()
            );
            return 1;
        }
    }

    @Override
    public RouteCacheLease acquireRefreshLock(Set<RouteCacheKey> keys) {
        if (keys.isEmpty()) return RouteCacheLease.bypass();
        String lockKey = refreshLockKey(keys);
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, token, properties.getRefreshLockTtl());
            if (Boolean.TRUE.equals(acquired)) {
                lockMetric("acquired");
                return RouteCacheLease.acquired(() -> release(lockKey, token));
            }
            lockMetric("contended");
            return RouteCacheLease.waiting();
        } catch (RuntimeException exception) {
            lockMetric("bypass");
            log.warn("Route Cache 갱신 잠금을 사용할 수 없어 잠금 없이 진행합니다: {}",
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
            if (latest.routes().size() == keys.size()) {
                waitMetric("filled", started);
                return latest;
            }
            if (latest.failureCount() > 0) {
                waitMetric("cache_failure", started);
                return latest;
            }
            try {
                Thread.sleep(properties.getRefreshPollInterval().toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                waitMetric("interrupted", started);
                return latest;
            }
        }
        latest = getAll(keys);
        waitMetric(latest.routes().size() == keys.size() ? "filled" : "timeout", started);
        return latest;
    }

    String redisKey(RouteCacheKey key) {
        return properties.getKeyPrefix()
                + ":google-routes:"
                + key.transportMode().name()
                + ":" + key.departureBucket(properties.getDepartureBucket()).getEpochSecond()
                + ":" + coordinate(key.origin())
                + ":" + coordinate(key.destination());
    }

    String refreshLockKey(Set<RouteCacheKey> keys) {
        String material = keys.stream().map(this::redisKey).sorted().reduce("", (left, right) -> left + "\n" + right);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return properties.getKeyPrefix() + ":refresh-lock:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void release(String lockKey, String token) {
        try {
            redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token);
        } catch (RuntimeException exception) {
            log.warn("Route Cache 갱신 잠금 해제에 실패했습니다: {}", exception.getClass().getSimpleName());
        }
    }

    private void lockMetric(String outcome) {
        if (stampedeMetrics != null) stampedeMetrics.lock(outcome);
    }

    private void waitMetric(String outcome, long started) {
        if (stampedeMetrics != null) {
            stampedeMetrics.waitCompleted(outcome, Math.max(0, (System.nanoTime() - started) / 1_000_000));
        }
    }

    private String coordinate(Location location) {
        return decimal(location.latitude()) + "," + decimal(location.longitude());
    }

    private String decimal(double coordinate) {
        return BigDecimal.valueOf(coordinate)
                .setScale(6, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String encode(RouteResult route) {
        return route.distanceMeters() + ":" + route.estimatedTravelMinutes();
    }

    private RouteResult decode(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Route Cache 값 형식이 올바르지 않습니다.");
        }
        try {
            return new RouteResult(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Route Cache 값을 해석할 수 없습니다.", exception);
        }
    }

    private Duration ttl(TransportMode transportMode) {
        return switch (transportMode) {
            case WALKING -> properties.getWalkingTtl();
            case DRIVING -> properties.getDrivingTtl();
            case PUBLIC_TRANSIT -> properties.getTransitTtl();
        };
    }

    private byte[] serialize(RedisSerializer<String> serializer, String value) {
        byte[] serialized = serializer.serialize(value);
        if (serialized == null) {
            throw new IllegalStateException("Route Cache 값을 직렬화할 수 없습니다.");
        }
        return serialized;
    }
}
