package com.routeplan.optimization.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.google.GoogleMapsHttpClient;
import com.routeplan.integration.google.GoogleMapsProperties;
import com.routeplan.integration.retry.ExternalApiOperation;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.cache.RouteCacheKey;
import com.routeplan.optimization.route.cache.RouteCacheRead;
import com.routeplan.optimization.route.cache.RouteLegCache;
import com.routeplan.trip.domain.TransportMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.route",
        name = "provider",
        havingValue = "GOOGLE"
)
public class GoogleRoutesMatrixProvider implements RouteMatrixProvider {

    private static final String FIELD_MASK =
            "originIndex,destinationIndex,status,condition,distanceMeters,duration";
    private static final int STANDARD_MAX_ELEMENTS = 625;
    private static final int TRANSIT_MAX_ELEMENTS = 100;

    private final GoogleMapsHttpClient httpClient;
    private final URI endpoint;
    private final RouteLegCache routeLegCache;

    public GoogleRoutesMatrixProvider(
            GoogleMapsHttpClient httpClient,
            GoogleMapsProperties properties,
            RouteLegCache routeLegCache
    ) {
        this.httpClient = httpClient;
        this.endpoint = properties.getRoutesBaseUrl()
                .resolve("/distanceMatrix/v2:computeRouteMatrix");
        this.routeLegCache = routeLegCache;
    }

    @Override
    public RouteMatrix build(List<Location> locations, TransportMode transportMode) {
        return build(locations, transportMode, null);
    }

    @Override
    public RouteMatrix build(List<Location> locations, TransportMode transportMode, java.time.Instant departure) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("Route Matrix 위치가 한 개 이상 필요합니다.");
        }
        List<Location> uniqueLocations = locations.stream().distinct().toList();
        int chunkSize = transportMode == TransportMode.PUBLIC_TRANSIT
                ? (int) Math.sqrt(TRANSIT_MAX_ELEMENTS)
                : (int) Math.sqrt(STANDARD_MAX_ELEMENTS);
        long startedAt = System.nanoTime();
        List<List<Location>> chunks = partition(uniqueLocations, chunkSize);
        Map<RouteMatrix.Leg, RouteResult> routes = new LinkedHashMap<>();
        addZeroDistanceRoutes(uniqueLocations, routes);
        Set<RouteCacheKey> cacheKeys = cacheKeys(uniqueLocations, transportMode);
        // A transit matrix depends on its actual departure date. Never reuse timeless cached legs.
        boolean useCache = routeLegCache.enabled() && !(transportMode == TransportMode.PUBLIC_TRANSIT && departure != null);
        RouteCacheRead cacheRead = useCache ? readCache(cacheKeys) : RouteCacheRead.empty();
        cacheRead.routes().forEach((key, route) -> routes.put(
                new RouteMatrix.Leg(key.origin(), key.destination()),
                route
        ));
        Map<RouteCacheKey, RouteResult> fetchedRoutes = new LinkedHashMap<>();
        int requestCount = 0;

        for (List<Location> origins : chunks) {
            for (List<Location> destinations : chunks) {
                if (containsAll(routes, origins, destinations)) {
                    continue;
                }
                JsonNode response = httpClient.post(
                        ExternalApiOperation.GOOGLE_ROUTES,
                        endpoint,
                        FIELD_MASK,
                        requestBody(origins, destinations, transportMode, departure)
                );
                requestCount++;
                merge(response, origins, destinations, transportMode, routes, fetchedRoutes);
            }
        }
        int cacheFailures = cacheRead.failureCount();
        if (useCache) {
            cacheFailures += routeLegCache.putAll(fetchedRoutes);
        }
        verifyComplete(uniqueLocations, routes);
        return new RouteMatrix(
                transportMode,
                RouteDataType.GOOGLE_ROUTES,
                routes,
                requestCount,
                elapsedMillis(startedAt),
                useCache,
                cacheRead.routes().size(),
                useCache ? cacheKeys.size() - cacheRead.routes().size() : 0,
                cacheFailures
        );
    }

    /** One billable matrix element at an exact departure, never a timeless cache lookup. */
    public RouteResult transitLeg(Location origin, Location destination, java.time.Instant departure) {
        if (origin.equals(destination)) return new RouteResult(0, 0);
        var response = httpClient.post(ExternalApiOperation.GOOGLE_ROUTES, endpoint, FIELD_MASK,
                requestBody(List.of(origin), List.of(destination), TransportMode.PUBLIC_TRANSIT, departure));
        Map<RouteMatrix.Leg, RouteResult> routes = new LinkedHashMap<>();
        merge(response, List.of(origin), List.of(destination), TransportMode.PUBLIC_TRANSIT, routes, new LinkedHashMap<>());
        RouteResult result = routes.get(new RouteMatrix.Leg(origin, destination));
        if (result == null) throw invalidResponse("출발 시각별 경로가 응답에 없습니다.");
        return result;
    }

    private void addZeroDistanceRoutes(
            List<Location> locations,
            Map<RouteMatrix.Leg, RouteResult> routes
    ) {
        locations.forEach(location -> routes.put(
                new RouteMatrix.Leg(location, location),
                new RouteResult(0, 0)
        ));
    }

    private Set<RouteCacheKey> cacheKeys(
            List<Location> locations,
            TransportMode transportMode
    ) {
        return locations.stream()
                .flatMap(origin -> locations.stream()
                        .filter(destination -> !origin.equals(destination))
                        .map(destination -> new RouteCacheKey(origin, destination, transportMode)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private RouteCacheRead readCache(Set<RouteCacheKey> cacheKeys) {
        return routeLegCache.enabled()
                ? routeLegCache.getAll(cacheKeys)
                : RouteCacheRead.empty();
    }

    private boolean containsAll(
            Map<RouteMatrix.Leg, RouteResult> routes,
            List<Location> origins,
            List<Location> destinations
    ) {
        return origins.stream().allMatch(origin -> destinations.stream()
                .allMatch(destination -> routes.containsKey(new RouteMatrix.Leg(origin, destination))));
    }

    private Map<String, Object> requestBody(
            List<Location> origins,
            List<Location> destinations,
            TransportMode transportMode, java.time.Instant departure
    ) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "origins", origins.stream().map(this::origin).toList(),
                "destinations", destinations.stream().map(this::destination).toList(),
                "travelMode", googleTravelMode(transportMode)
        ));
        if (transportMode == TransportMode.PUBLIC_TRANSIT && departure != null) {
            java.time.Instant now = java.time.Instant.now();
            if (departure.isBefore(now.minus(java.time.Duration.ofDays(7)))
                    || departure.isAfter(now.plus(java.time.Duration.ofDays(100)))) {
                throw new IllegalArgumentException("대중교통 조회는 현재 기준 과거 7일~미래 100일만 지원합니다.");
            }
            body.put("departureTime", departure.toString());
        }
        return body;
    }

    private Map<String, Object> origin(Location location) {
        return Map.of("waypoint", waypoint(location));
    }

    private Map<String, Object> destination(Location location) {
        return Map.of("waypoint", waypoint(location));
    }

    private Map<String, Object> waypoint(Location location) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", location.latitude(),
                "longitude", location.longitude()
        )));
    }

    private String googleTravelMode(TransportMode transportMode) {
        return switch (transportMode) {
            case WALKING -> "WALK";
            case PUBLIC_TRANSIT -> "TRANSIT";
            case DRIVING -> "DRIVE";
        };
    }

    private void merge(
            JsonNode response,
            List<Location> origins,
            List<Location> destinations,
            TransportMode transportMode,
            Map<RouteMatrix.Leg, RouteResult> routes,
            Map<RouteCacheKey, RouteResult> fetchedRoutes
    ) {
        if (!response.isArray()) {
            throw invalidResponse("Route Matrix 응답이 배열이 아닙니다.");
        }
        for (JsonNode element : response) {
            int originIndex = requiredIndex(element, "originIndex", origins.size());
            int destinationIndex = requiredIndex(element, "destinationIndex", destinations.size());
            Location origin = origins.get(originIndex);
            Location destination = destinations.get(destinationIndex);
            if (origin.equals(destination)) {
                routes.put(new RouteMatrix.Leg(origin, destination), new RouteResult(0, 0));
                continue;
            }
            validateElementStatus(element, origin, destination);
            long distanceMeters = requiredNonNegativeLong(element, "distanceMeters");
            int travelMinutes = durationMinutes(requiredText(element, "duration"), distanceMeters);
            RouteResult route = new RouteResult(distanceMeters, travelMinutes);
            routes.put(
                    new RouteMatrix.Leg(origin, destination),
                    route
            );
            fetchedRoutes.put(new RouteCacheKey(origin, destination, transportMode), route);
        }
    }

    private void validateElementStatus(
            JsonNode element,
            Location origin,
            Location destination
    ) {
        int statusCode = element.path("status").path("code").asInt(0);
        String condition = element.path("condition").asText("");
        if (statusCode != 0 || "ROUTE_NOT_FOUND".equals(condition)) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.ROUTE_NOT_FOUND,
                    "이동 가능한 경로를 찾을 수 없습니다: " + origin + " -> " + destination
            );
        }
        if (!"ROUTE_EXISTS".equals(condition)) {
            throw invalidResponse("Route Matrix element condition이 올바르지 않습니다.");
        }
    }

    private int requiredIndex(JsonNode element, String field, int size) {
        JsonNode value = element.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw invalidResponse("Route Matrix index가 누락됐습니다: " + field);
        }
        int index = value.asInt();
        if (index < 0 || index >= size) {
            throw invalidResponse("Route Matrix index 범위를 벗어났습니다: " + field);
        }
        return index;
    }

    private long requiredNonNegativeLong(JsonNode element, String field) {
        JsonNode value = element.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() < 0) {
            throw invalidResponse("Route Matrix 거리값이 올바르지 않습니다.");
        }
        return value.asLong();
    }

    private String requiredText(JsonNode element, String field) {
        JsonNode value = element.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalidResponse("Route Matrix duration이 누락됐습니다.");
        }
        return value.asText();
    }

    private int durationMinutes(String duration, long distanceMeters) {
        if (!duration.endsWith("s")) {
            throw invalidResponse("Route Matrix duration 형식이 올바르지 않습니다.");
        }
        try {
            BigDecimal seconds = new BigDecimal(duration.substring(0, duration.length() - 1));
            if (seconds.signum() < 0) {
                throw invalidResponse("Route Matrix duration은 0 이상이어야 합니다.");
            }
            int minutes = seconds.divide(BigDecimal.valueOf(60), 0, RoundingMode.CEILING)
                    .intValueExact();
            return distanceMeters == 0 ? 0 : Math.max(1, minutes);
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.INVALID_RESPONSE,
                    "Route Matrix duration을 해석할 수 없습니다.",
                    exception
            );
        }
    }

    private void verifyComplete(
            List<Location> locations,
            Map<RouteMatrix.Leg, RouteResult> routes
    ) {
        int expectedElements = Math.multiplyExact(locations.size(), locations.size());
        if (routes.size() != expectedElements) {
            throw invalidResponse(
                    "Route Matrix element가 누락됐습니다: expected="
                            + expectedElements + ", actual=" + routes.size()
            );
        }
    }

    private List<List<Location>> partition(List<Location> locations, int chunkSize) {
        List<List<Location>> result = new ArrayList<>();
        for (int start = 0; start < locations.size(); start += chunkSize) {
            result.add(locations.subList(start, Math.min(start + chunkSize, locations.size())));
        }
        return List.copyOf(result);
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private ExternalProviderException invalidResponse(String message) {
        return new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message);
    }
}
