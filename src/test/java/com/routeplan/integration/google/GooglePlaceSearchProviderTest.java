package com.routeplan.integration.google;

import static com.routeplan.integration.retry.ExternalRetryTestSupport.noDelayRetryExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.optimization.domain.Location;
import com.routeplan.place.search.GooglePlaceSearchProvider;
import com.routeplan.place.search.PlaceSearchQuery;
import com.routeplan.place.search.PlaceSearchResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GooglePlaceSearchProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void searchesPlacesWithFieldMaskAndLocationBias() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(200, """
                    {
                      "places":[{
                        "id":"google-place-1",
                        "displayName":{"text":"오사카성","languageCode":"ko"},
                        "formattedAddress":"1-1 Osakajo, Chuo Ward, Osaka",
                        "location":{"latitude":34.6873,"longitude":135.5262},
                        "primaryType":"historical_landmark"
                      }]
                    }
                    """));
            GooglePlaceSearchProvider provider = provider(server, "test-key");

            List<PlaceSearchResult> results = provider.search(new PlaceSearchQuery(
                    "오사카성",
                    Location.of(BigDecimal.valueOf(34.66), BigDecimal.valueOf(135.50)),
                    5_000,
                    5,
                    "ko"
            ));

            assertThat(results).containsExactly(new PlaceSearchResult(
                    "google-place-1",
                    "오사카성",
                    "1-1 Osakajo, Chuo Ward, Osaka",
                    new BigDecimal("34.6873"),
                    new BigDecimal("135.5262"),
                    "historical_landmark",
                    "GOOGLE_PLACES"
            ));
            GoogleMapsStubServer.RecordedRequest request = server.requests().getFirst();
            assertThat(request.path()).isEqualTo("/v1/places:searchText");
            assertThat(request.header("X-Goog-Api-Key")).isEqualTo("test-key");
            assertThat(request.header("X-Goog-FieldMask")).contains("places.location");
            JsonNode body = objectMapper.readTree(request.body());
            assertThat(body.path("pageSize").asInt()).isEqualTo(5);
            assertThat(body.path("locationBias").path("circle").path("radius").asInt())
                    .isEqualTo(5_000);
        }
    }

    @Test
    void mapsRateLimitWithoutLeakingApiKey() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(429, "{}"));
            GooglePlaceSearchProvider provider = provider(server, "secret-test-key");

            assertThatThrownBy(() -> provider.search(new PlaceSearchQuery(
                    "오사카성", null, 0, 5, "ko"
            )))
                    .isInstanceOfSatisfying(ExternalProviderException.class, exception -> {
                        assertThat(exception.failure()).isEqualTo(ExternalProviderFailure.RATE_LIMITED);
                        assertThat(exception.getMessage()).doesNotContain("secret-test-key");
                    });
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test
    void retriesTransientFailuresAndReturnsSuccessfulResponse() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            AtomicInteger attempts = new AtomicInteger();
            server.respondWith(request -> attempts.incrementAndGet() < 3
                    ? new GoogleMapsStubServer.StubResponse(503, "{}")
                    : new GoogleMapsStubServer.StubResponse(200, """
                            {
                              "places":[{
                                "id":"google-place-1",
                                "displayName":{"text":"오사카성"},
                                "formattedAddress":"Osaka",
                                "location":{"latitude":34.6873,"longitude":135.5262}
                              }]
                            }
                            """));
            GooglePlaceSearchProvider provider = provider(server, "test-key");

            List<PlaceSearchResult> results = provider.search(new PlaceSearchQuery(
                    "오사카성", null, 0, 5, "ko"
            ));

            assertThat(results).hasSize(1);
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test
    void doesNotRetryNonTransientClientFailure() throws Exception {
        try (GoogleMapsStubServer server = new GoogleMapsStubServer()) {
            server.respondWith(request -> new GoogleMapsStubServer.StubResponse(400, "{}"));
            GooglePlaceSearchProvider provider = provider(server, "test-key");

            assertThatThrownBy(() -> provider.search(new PlaceSearchQuery(
                    "오사카성", null, 0, 5, "ko"
            )))
                    .isInstanceOfSatisfying(ExternalProviderException.class, exception ->
                            assertThat(exception.failure())
                                    .isEqualTo(ExternalProviderFailure.INVALID_RESPONSE));
            assertThat(server.requests()).hasSize(1);
        }
    }

    private GooglePlaceSearchProvider provider(GoogleMapsStubServer server, String apiKey) {
        GoogleMapsProperties properties = new GoogleMapsProperties();
        properties.setApiKey(apiKey);
        properties.setPlacesBaseUrl(server.baseUri());
        return new GooglePlaceSearchProvider(
                new GoogleMapsHttpClient(properties, noDelayRetryExecutor(3)),
                properties
        );
    }
}
