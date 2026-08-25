package com.routeplan.integration.google;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.route.GoogleRoutesMatrixProvider;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.cache.DisabledRouteLegCache;
import com.routeplan.optimization.route.cache.RedisRouteLegCache;
import com.routeplan.optimization.route.cache.RouteCacheProperties;
import com.routeplan.optimization.route.cache.RouteLegCache;
import com.routeplan.trip.domain.TransportMode;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("cache-benchmark")
@Testcontainers
class RouteCacheBenchmarkTest {

    private static final int REDIS_PORT = 6379;
    private static final int LOCATION_COUNT = 21;
    private static final int MEASURED_RUNS = 15;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(REDIS_PORT);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void comparesUncachedAndWarmRedisMatrixBuilds() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer();
                RedisFixture redis = redisFixture()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(
                    200,
                    matrixResponse(request.body())
            ));
            List<Location> locations = IntStream.range(0, LOCATION_COUNT)
                    .mapToObj(index -> new Location(34.0 + index * 0.001, 135.0))
                    .toList();
            GoogleRoutesMatrixProvider uncached = provider(
                    server,
                    new DisabledRouteLegCache()
            );
            GoogleRoutesMatrixProvider cached = provider(
                    server,
                    new RedisRouteLegCache(redis.template(), cacheProperties())
            );

            uncached.build(locations, TransportMode.WALKING);
            List<Double> uncachedMillis = measure(uncached, locations, MEASURED_RUNS);
            RouteMatrix cold = cached.build(locations, TransportMode.WALKING);
            List<RouteMatrix> warmMatrices = new ArrayList<>();
            List<Double> warmMillis = measure(cached, locations, MEASURED_RUNS, warmMatrices);

            assertThat(cold.providerCallCount()).isEqualTo(1);
            assertThat(cold.cacheMissCount()).isEqualTo(420);
            assertThat(warmMatrices).allSatisfy(matrix -> {
                assertThat(matrix.providerCallCount()).isZero();
                assertThat(matrix.cacheHitCount()).isEqualTo(420);
                assertThat(matrix.cacheHitRatio()).isEqualTo(1.0);
            });

            System.out.printf(Locale.ROOT,
                    "CACHE_BENCHMARK,locations=%d,matrix_elements=%d,runs=%d,"
                            + "uncached_provider_calls=%d,cold_cache_provider_calls=%d,"
                            + "warm_cache_provider_calls=%d,cache_hit_ratio=%.2f,"
                            + "uncached_median_ms=%.3f,warm_cache_median_ms=%.3f%n",
                    LOCATION_COUNT,
                    cold.elementCount(),
                    MEASURED_RUNS,
                    MEASURED_RUNS,
                    cold.providerCallCount(),
                    warmMatrices.stream().mapToInt(RouteMatrix::providerCallCount).sum(),
                    warmMatrices.getFirst().cacheHitRatio(),
                    median(uncachedMillis),
                    median(warmMillis)
            );
        }
    }

    private List<Double> measure(
            GoogleRoutesMatrixProvider provider,
            List<Location> locations,
            int runs
    ) {
        return measure(provider, locations, runs, new ArrayList<>());
    }

    private List<Double> measure(
            GoogleRoutesMatrixProvider provider,
            List<Location> locations,
            int runs,
            List<RouteMatrix> matrices
    ) {
        List<Double> millis = new ArrayList<>();
        for (int index = 0; index < runs; index++) {
            long startedAt = System.nanoTime();
            RouteMatrix matrix = provider.build(locations, TransportMode.WALKING);
            millis.add((System.nanoTime() - startedAt) / 1_000_000.0);
            matrices.add(matrix);
        }
        return List.copyOf(millis);
    }

    private double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        return sorted.get(sorted.size() / 2);
    }

    private GoogleRoutesMatrixProvider provider(
            GoogleMapsStubServer server,
            RouteLegCache routeLegCache
    ) {
        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setApiKey("benchmark-key");
        properties.setRoutesBaseUrl(server.baseUri());
        return new GoogleRoutesMatrixProvider(
                new GoogleMapsHttpClient(properties),
                properties,
                routeLegCache
        );
    }

    private RedisFixture redisFixture() {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(server, client);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return new RedisFixture(connectionFactory, template);
    }

    private RouteCacheProperties cacheProperties() {
        RouteCacheProperties properties = new RouteCacheProperties();
        properties.setKeyPrefix("routeplan:benchmark:v1");
        return properties;
    }

    private String matrixResponse(String requestBody) {
        try {
            JsonNode request = objectMapper.readTree(requestBody);
            ArrayNode response = objectMapper.createArrayNode();
            for (int origin = 0; origin < request.path("origins").size(); origin++) {
                for (int destination = 0;
                        destination < request.path("destinations").size();
                        destination++) {
                    ObjectNode element = response.addObject();
                    element.put("originIndex", origin);
                    element.put("destinationIndex", destination);
                    element.putObject("status").put("code", 0);
                    element.put("condition", "ROUTE_EXISTS");
                    if (origin != destination) {
                        element.put("distanceMeters", 1_000 + origin * 10L + destination);
                        element.put("duration", "900s");
                    }
                }
            }
            return objectMapper.writeValueAsString(response);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
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
