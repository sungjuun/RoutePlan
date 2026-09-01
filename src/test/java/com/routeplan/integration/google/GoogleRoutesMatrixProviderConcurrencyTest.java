package com.routeplan.integration.google;

import static com.routeplan.integration.retry.ExternalRetryTestSupport.noDelayRetryExecutor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.route.GoogleRoutesMatrixProvider;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.cache.RedisRouteLegCache;
import com.routeplan.optimization.route.cache.RouteCacheProperties;
import com.routeplan.trip.domain.TransportMode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class GoogleRoutesMatrixProviderConcurrencyTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void twoProviderInstancesCollapseTheSameConcurrentMatrixMiss() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer();
                RedisFixture firstRedis = redisFixture(); RedisFixture secondRedis = redisFixture()) {
            server.respondWith(request -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return new GoogleMapsStubServer.StubResponse(200, matrixResponse(request.body()));
            });
            String prefix = "routeplan:concurrency:" + UUID.randomUUID();
            GoogleRoutesMatrixProvider first = provider(server, firstRedis.template(), properties(prefix));
            GoogleRoutesMatrixProvider second = provider(server, secondRedis.template(), properties(prefix));
            List<Location> locations = List.of(location(35.68, 139.76), location(35.69, 139.77));
            Instant departure = Instant.now().plus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
            CountDownLatch start = new CountDownLatch(1);
            Callable<RouteMatrix> firstCall = () -> { start.await(5, TimeUnit.SECONDS); return first.build(locations, TransportMode.DRIVING, departure); };
            Callable<RouteMatrix> secondCall = () -> { start.await(5, TimeUnit.SECONDS); return second.build(locations, TransportMode.DRIVING, departure); };

            try (var executor = Executors.newFixedThreadPool(2)) {
                var firstResult = executor.submit(firstCall);
                var secondResult = executor.submit(secondCall);
                start.countDown();
                List<RouteMatrix> matrices = List.of(
                        firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));

                assertThat(server.requests()).hasSize(1);
                assertThat(matrices).allSatisfy(matrix -> assertThat(matrix.cacheFailureCount()).isZero());
                assertThat(matrices.stream().mapToInt(RouteMatrix::providerCallCount).sum()).isEqualTo(1);
                assertThat(matrices.stream().mapToInt(RouteMatrix::cacheHitCount).sum()).isEqualTo(2);
            }
        }
    }

    private GoogleRoutesMatrixProvider provider(
            GoogleMapsStubServer server, StringRedisTemplate redis, RouteCacheProperties cacheProperties
    ) {
        GoogleMapsProperties maps = new GoogleMapsProperties();
        maps.setApiKey("concurrency-test-key");
        maps.setRoutesBaseUrl(server.baseUri());
        return new GoogleRoutesMatrixProvider(
                new GoogleMapsHttpClient(maps, noDelayRetryExecutor(1)), maps,
                new RedisRouteLegCache(redis, cacheProperties));
    }

    private RouteCacheProperties properties(String prefix) {
        RouteCacheProperties properties = new RouteCacheProperties();
        properties.setKeyPrefix(prefix);
        properties.setDrivingTtl(Duration.ofMinutes(5));
        properties.setRefreshLockTtl(Duration.ofSeconds(3));
        properties.setRefreshWait(Duration.ofSeconds(2));
        properties.setRefreshPollInterval(Duration.ofMillis(20));
        return properties;
    }

    private RedisFixture redisFixture() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2)).shutdownTimeout(Duration.ZERO).build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return new RedisFixture(factory, template);
    }

    private String matrixResponse(String requestBody) {
        try {
            JsonNode request = objectMapper.readTree(requestBody);
            ArrayNode response = objectMapper.createArrayNode();
            for (int origin = 0; origin < request.path("origins").size(); origin++) {
                for (int destination = 0; destination < request.path("destinations").size(); destination++) {
                    ObjectNode element = response.addObject();
                    element.put("originIndex", origin);
                    element.put("destinationIndex", destination);
                    element.putObject("status").put("code", 0);
                    element.put("condition", "ROUTE_EXISTS");
                    if (origin != destination) {
                        element.put("distanceMeters", 1_000 + origin * 100L + destination);
                        element.put("duration", "600s");
                    }
                }
            }
            return objectMapper.writeValueAsString(response);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private Location location(double latitude, double longitude) {
        return Location.of(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private record RedisFixture(LettuceConnectionFactory factory, StringRedisTemplate template)
            implements AutoCloseable {
        @Override public void close() { factory.destroy(); }
    }
}
