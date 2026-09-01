package com.routeplan.optimization.route.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRouteLegCacheIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(REDIS_PORT);

    @Test
    void storesDirectedLegWithModeSpecificTtl() {
        try (RedisFixture fixture = fixture(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT))) {
            RouteCacheProperties properties = properties();
            RedisRouteLegCache cache = new RedisRouteLegCache(fixture.template(), properties);
            RouteCacheKey walking = key(TransportMode.WALKING);

            RouteCacheRead miss = cache.getAll(Set.of(walking));
            int writeFailures = cache.putAll(Map.of(walking, new RouteResult(1_250, 18)));
            RouteCacheRead hit = cache.getAll(Set.of(walking));
            RouteCacheRead differentMode = cache.getAll(Set.of(key(TransportMode.DRIVING)));
            Long ttlSeconds = fixture.template().getExpire(cache.redisKey(walking));

            assertThat(miss.routes()).isEmpty();
            assertThat(writeFailures).isZero();
            assertThat(hit.routes()).containsEntry(walking, new RouteResult(1_250, 18));
            assertThat(hit.failureCount()).isZero();
            assertThat(differentMode.routes()).isEmpty();
            assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(30);
            assertThat(cache.redisKey(walking))
                    .isEqualTo("routeplan:test:v1:google-routes:WALKING:0:"
                            + "34.123457,135.765432:34.223457,135.865432");
        }
    }

    @Test
    void returnsMissAndFailureInsteadOfPropagatingRedisOutage() {
        try (RedisFixture fixture = fixture("127.0.0.1", 1)) {
            RedisRouteLegCache cache = new RedisRouteLegCache(fixture.template(), properties());
            RouteCacheKey key = key(TransportMode.PUBLIC_TRANSIT);

            RouteCacheRead read = cache.getAll(Set.of(key));
            int writeFailures = cache.putAll(Map.of(key, new RouteResult(500, 7)));

            assertThat(read.routes()).isEmpty();
            assertThat(read.failureCount()).isEqualTo(1);
            assertThat(writeFailures).isEqualTo(1);
        }
    }

    @Test
    void coordinatesRefreshWithAnOwnershipCheckedDistributedLock() {
        try (RedisFixture fixture = fixture(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT))) {
            RedisRouteLegCache cache = new RedisRouteLegCache(fixture.template(), properties());
            RouteCacheKey key = key(TransportMode.WALKING);

            RouteCacheLease leader = cache.acquireRefreshLock(Set.of(key));
            RouteCacheLease follower = cache.acquireRefreshLock(Set.of(key));
            assertThat(leader.acquired()).isTrue();
            assertThat(follower.contended()).isTrue();

            cache.putAll(Map.of(key, new RouteResult(1_250, 18)));
            assertThat(cache.waitForRefresh(Set.of(key)).routes())
                    .containsEntry(key, new RouteResult(1_250, 18));

            follower.close();
            leader.close();
            try (RouteCacheLease next = cache.acquireRefreshLock(Set.of(key))) {
                assertThat(next.acquired()).isTrue();
            }
        }
    }

    private RedisFixture fixture(String host, int port) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(250))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(server, client);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return new RedisFixture(connectionFactory, template);
    }

    private RouteCacheProperties properties() {
        RouteCacheProperties properties = new RouteCacheProperties();
        properties.setKeyPrefix("routeplan:test:v1");
        properties.setWalkingTtl(Duration.ofSeconds(30));
        properties.setDrivingTtl(Duration.ofSeconds(20));
        properties.setTransitTtl(Duration.ofSeconds(10));
        properties.setRefreshLockTtl(Duration.ofSeconds(5));
        properties.setRefreshWait(Duration.ofMillis(500));
        properties.setRefreshPollInterval(Duration.ofMillis(25));
        return properties;
    }

    private RouteCacheKey key(TransportMode transportMode) {
        return new RouteCacheKey(
                new Location(34.1234567, 135.7654321),
                new Location(34.2234567, 135.8654321),
                transportMode
        );
    }

    private record RedisFixture(
            LettuceConnectionFactory connectionFactory,
            StringRedisTemplate template
    ) implements AutoCloseable {

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
