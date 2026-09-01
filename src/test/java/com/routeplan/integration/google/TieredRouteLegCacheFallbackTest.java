package com.routeplan.integration.google;

import static com.routeplan.integration.retry.ExternalRetryTestSupport.noDelayRetryExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.GoogleRoutesMatrixProvider;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.cache.PostgisRouteLegCache;
import com.routeplan.optimization.route.cache.RedisRouteLegCache;
import com.routeplan.optimization.route.cache.RouteCacheKey;
import com.routeplan.optimization.route.cache.RouteCacheLease;
import com.routeplan.optimization.route.cache.RouteCacheRead;
import com.routeplan.optimization.route.cache.RouteCacheTierMetrics;
import com.routeplan.optimization.route.cache.TieredRouteLegCache;
import com.routeplan.trip.domain.TransportMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TieredRouteLegCacheFallbackTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void redisFailureFallsThroughToPostgisAndWarmsRedis() {
        RedisRouteLegCache redis = mock(RedisRouteLegCache.class);
        PostgisRouteLegCache database = mock(PostgisRouteLegCache.class);
        TieredRouteLegCache cache = tiered(redis, database);
        RouteCacheKey first = key(location(34.1, 135.1), location(34.2, 135.2));
        RouteCacheKey second = key(first.destination(), first.origin());
        Map<RouteCacheKey, RouteResult> stored = new LinkedHashMap<>();
        stored.put(first, new RouteResult(1_200, 4));
        stored.put(second, new RouteResult(1_300, 5));
        when(redis.getAll(any())).thenReturn(new RouteCacheRead(Map.of(), 1));
        when(database.getAll(any())).thenReturn(new RouteCacheRead(stored, 0));
        when(redis.putAll(any())).thenReturn(0);

        RouteCacheRead result = cache.getAll(Set.of(first, second));

        assertThat(result.routes()).containsAllEntriesOf(stored);
        assertThat(result.failureCount()).isEqualTo(1);
        verify(database).getAll(Set.of(first, second));
        verify(redis).putAll(stored);
    }

    @Test
    void postgisFailureStillReturnsGoogleResultAndWritesAvailableRedisTier() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(200, matrixResponse(request.body())));
            RedisRouteLegCache redis = mock(RedisRouteLegCache.class);
            PostgisRouteLegCache database = mock(PostgisRouteLegCache.class);
            when(redis.getAll(any())).thenReturn(RouteCacheRead.empty());
            when(database.getAll(any())).thenReturn(new RouteCacheRead(Map.of(), 1));
            when(redis.acquireRefreshLock(any())).thenReturn(RouteCacheLease.acquired(() -> {}));
            when(database.putAll(any())).thenReturn(1);
            when(redis.putAll(any())).thenReturn(0);
            TieredRouteLegCache cache = tiered(redis, database);
            GoogleRoutesMatrixProvider routes = provider(server, cache);
            List<Location> locations = List.of(location(34.1, 135.1), location(34.2, 135.2));

            RouteMatrix matrix = routes.build(locations, TransportMode.DRIVING,
                    java.time.Instant.now().plus(java.time.Duration.ofDays(2)));

            assertThat(matrix.providerCallCount()).isEqualTo(1);
            assertThat(matrix.cacheFailureCount()).isEqualTo(2);
            assertThat(matrix.getRoute(locations.getFirst(), locations.getLast(), TransportMode.DRIVING))
                    .isEqualTo(new RouteResult(100, 2));
            assertThat(server.requests()).hasSize(1);
            verify(redis).putAll(any());
            verify(database).putAll(any());
        }
    }

    private TieredRouteLegCache tiered(RedisRouteLegCache redis, PostgisRouteLegCache database) {
        return new TieredRouteLegCache(providerOf(redis), providerOf(database),
                new RouteCacheTierMetrics(new SimpleMeterRegistry()));
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private GoogleRoutesMatrixProvider provider(GoogleMapsStubServer server, TieredRouteLegCache cache) {
        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setApiKey("tier-fallback-test-key");
        properties.setRoutesBaseUrl(server.baseUri());
        return new GoogleRoutesMatrixProvider(
                new GoogleMapsHttpClient(properties, noDelayRetryExecutor(1)), properties, cache);
    }

    private RouteCacheKey key(Location origin, Location destination) {
        return new RouteCacheKey(origin, destination, TransportMode.DRIVING,
                java.time.Instant.parse("2026-09-10T01:00:00Z"));
    }

    private Location location(double latitude, double longitude) {
        return Location.of(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
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
                        element.put("distanceMeters", 100);
                        element.put("duration", "90s");
                    }
                }
            }
            return objectMapper.writeValueAsString(response);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
