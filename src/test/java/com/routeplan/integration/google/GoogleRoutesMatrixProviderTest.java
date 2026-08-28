package com.routeplan.integration.google;

import static com.routeplan.integration.retry.ExternalRetryTestSupport.noDelayRetryExecutor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.GoogleRoutesMatrixProvider;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.cache.DisabledRouteLegCache;
import com.routeplan.optimization.route.cache.RouteCacheKey;
import com.routeplan.optimization.route.cache.RouteCacheRead;
import com.routeplan.optimization.route.cache.RouteLegCache;
import com.routeplan.trip.domain.TransportMode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GoogleRoutesMatrixProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsMatrixUsingOfficialHeadersAndResponseIndices() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(
                    200,
                    matrixResponse(request.body())
            ));
            GoogleRoutesMatrixProvider provider = provider(server);
            List<Location> locations = List.of(location(34.1, 135.1), location(34.2, 135.2));

            RouteMatrix matrix = provider.build(locations, TransportMode.WALKING);

            assertThat(matrix.dataType()).isEqualTo(RouteDataType.GOOGLE_ROUTES);
            assertThat(matrix.providerCallCount()).isEqualTo(1);
            assertThat(matrix.elementCount()).isEqualTo(4);
            assertThat(matrix.cacheEnabled()).isFalse();
            assertThat(matrix.getRoute(
                    locations.getFirst(), locations.getLast(), TransportMode.WALKING
            ).estimatedTravelMinutes()).isEqualTo(2);
            GoogleMapsStubServer.RecordedRequest request = server.requests().getFirst();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/distanceMatrix/v2:computeRouteMatrix");
            assertThat(request.header("X-Goog-Api-Key")).isEqualTo("test-key");
            assertThat(request.header("X-Goog-FieldMask"))
                    .isEqualTo("originIndex,destinationIndex,status,condition,distanceMeters,duration");
            assertThat(objectMapper.readTree(request.body()).path("travelMode").asText())
                    .isEqualTo("WALK");
        }
    }

    @Test
    void reusesAllCachedLegsWithoutCallingGoogleAgain() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(
                    200,
                    matrixResponse(request.body())
            ));
            InMemoryRouteLegCache cache = new InMemoryRouteLegCache();
            GoogleRoutesMatrixProvider provider = provider(server, cache);
            List<Location> locations = List.of(location(34.1, 135.1), location(34.2, 135.2));

            RouteMatrix cold = provider.build(locations, TransportMode.WALKING);
            RouteMatrix warm = provider.build(locations, TransportMode.WALKING);

            assertThat(cold.providerCallCount()).isEqualTo(1);
            assertThat(cold.cacheHitCount()).isZero();
            assertThat(cold.cacheMissCount()).isEqualTo(2);
            assertThat(cold.cacheHitRatio()).isZero();
            assertThat(warm.providerCallCount()).isZero();
            assertThat(warm.cacheHitCount()).isEqualTo(2);
            assertThat(warm.cacheMissCount()).isZero();
            assertThat(warm.cacheHitRatio()).isEqualTo(1.0);
            assertThat(warm.getRoute(
                    locations.getFirst(), locations.getLast(), TransportMode.WALKING
            )).isEqualTo(cold.getRoute(
                    locations.getFirst(), locations.getLast(), TransportMode.WALKING
            ));
            assertThat(server.requests()).hasSize(1);
        }
    }

    @Test
    void fallsBackToGoogleWhenCacheReadAndWriteFail() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(
                    200,
                    matrixResponse(request.body())
            ));
            GoogleRoutesMatrixProvider provider = provider(server, new FailingRouteLegCache());
            List<Location> locations = List.of(location(34.1, 135.1), location(34.2, 135.2));

            RouteMatrix matrix = provider.build(locations, TransportMode.WALKING);

            assertThat(matrix.providerCallCount()).isEqualTo(1);
            assertThat(matrix.cacheHitCount()).isZero();
            assertThat(matrix.cacheMissCount()).isEqualTo(2);
            assertThat(matrix.cacheFailureCount()).isEqualTo(2);
            assertThat(matrix.elementCount()).isEqualTo(4);
        }
    }

    @Test
    void splitsTransitMatrixAtOneHundredElements() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(
                    200,
                    matrixResponse(request.body())
            ));
            GoogleRoutesMatrixProvider provider = provider(server);
            List<Location> locations = IntStream.range(0, 11)
                    .mapToObj(index -> location(34.0 + index * 0.01, 135.0))
                    .toList();

            RouteMatrix matrix = provider.build(locations, TransportMode.PUBLIC_TRANSIT);

            assertThat(matrix.providerCallCount()).isEqualTo(3);
            assertThat(matrix.elementCount()).isEqualTo(121);
            assertThat(server.requests()).hasSize(3);
            assertThat(server.requests()).allSatisfy(request -> {
                try {
                    JsonNode body = objectMapper.readTree(request.body());
                    int elements = body.path("origins").size() * body.path("destinations").size();
                    assertThat(elements).isLessThanOrEqualTo(100);
                    assertThat(body.path("travelMode").asText()).isEqualTo("TRANSIT");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
        }
    }

    private GoogleRoutesMatrixProvider provider(GoogleMapsStubServer server) {
        return provider(server, new DisabledRouteLegCache());
    }

    @Test
    void transitUsesEachTravelDateAndLocalDepartureWithoutReusingTimelessCache() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(200, matrixResponse(request.body())));
            var provider = provider(server, new InMemoryRouteLegCache());
            var date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Tokyo")).plusDays(2);
            var locations = List.of(location(34.1, 135.1), location(34.2, 135.2));
            var matrices = provider.buildForDates(locations, TransportMode.PUBLIC_TRANSIT, List.of(date, date.plusDays(1)),
                    java.time.LocalTime.of(10, 0), java.time.LocalTime.of(9, 0), "Asia/Tokyo");
            assertThat(server.requests()).hasSize(2);
            assertThat(objectMapper.readTree(server.requests().get(0).body()).path("departureTime").asText()).isEqualTo(date + "T01:00:00Z");
            assertThat(objectMapper.readTree(server.requests().get(1).body()).path("departureTime").asText()).isEqualTo(date.plusDays(1) + "T00:00:00Z");
            assertThat(RouteMatrix.summarize(matrices.values()).elementCount()).isEqualTo(8);
            assertThat(matrices.values()).allSatisfy(m -> assertThat(m.cacheEnabled()).isFalse());
        }
    }

    private GoogleRoutesMatrixProvider provider(
            GoogleMapsStubServer server,
            RouteLegCache routeLegCache
    ) {
        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setApiKey("test-key");
        properties.setRoutesBaseUrl(server.baseUri());
        return new GoogleRoutesMatrixProvider(
                new GoogleMapsHttpClient(properties, noDelayRetryExecutor(3)),
                properties,
                routeLegCache
        );
    }

    private String matrixResponse(String requestBody) {
        try {
            JsonNode request = objectMapper.readTree(requestBody);
            int originCount = request.path("origins").size();
            int destinationCount = request.path("destinations").size();
            ArrayNode response = objectMapper.createArrayNode();
            for (int origin = 0; origin < originCount; origin++) {
                for (int destination = 0; destination < destinationCount; destination++) {
                    JsonNode originLatLng = request.path("origins").path(origin)
                            .path("waypoint").path("location").path("latLng");
                    JsonNode destinationLatLng = request.path("destinations").path(destination)
                            .path("waypoint").path("location").path("latLng");
                    boolean sameLocation = originLatLng.equals(destinationLatLng);
                    ObjectNode element = response.addObject();
                    element.put("originIndex", origin);
                    element.put("destinationIndex", destination);
                    element.putObject("status").put("code", 0);
                    element.put("condition", "ROUTE_EXISTS");
                    if (!sameLocation) {
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

    private static Location location(double latitude, double longitude) {
        return Location.of(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }

    private static final class InMemoryRouteLegCache implements RouteLegCache {

        private final Map<RouteCacheKey, RouteResult> routes = new LinkedHashMap<>();

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public RouteCacheRead getAll(Set<RouteCacheKey> keys) {
            Map<RouteCacheKey, RouteResult> hits = new LinkedHashMap<>();
            keys.forEach(key -> {
                RouteResult route = routes.get(key);
                if (route != null) {
                    hits.put(key, route);
                }
            });
            return new RouteCacheRead(hits, 0);
        }

        @Override
        public int putAll(Map<RouteCacheKey, RouteResult> fetchedRoutes) {
            routes.putAll(fetchedRoutes);
            return 0;
        }
    }

    private static final class FailingRouteLegCache implements RouteLegCache {

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public RouteCacheRead getAll(Set<RouteCacheKey> keys) {
            return new RouteCacheRead(Map.of(), 1);
        }

        @Override
        public int putAll(Map<RouteCacheKey, RouteResult> routes) {
            return 1;
        }
    }
}
