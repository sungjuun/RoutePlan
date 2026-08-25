package com.routeplan.integration.google;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.route.GoogleRoutesMatrixProvider;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.trip.domain.TransportMode;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
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

            assertThat(matrix.providerCallCount()).isEqualTo(4);
            assertThat(matrix.elementCount()).isEqualTo(121);
            assertThat(server.requests()).hasSize(4);
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
        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setApiKey("test-key");
        properties.setRoutesBaseUrl(server.baseUri());
        return new GoogleRoutesMatrixProvider(
                new GoogleMapsHttpClient(properties),
                properties
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
}
