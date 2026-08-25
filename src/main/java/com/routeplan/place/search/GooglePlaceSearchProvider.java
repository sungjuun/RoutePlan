package com.routeplan.place.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.google.GoogleMapsHttpClient;
import com.routeplan.integration.google.GoogleMapsProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.place",
        name = "provider",
        havingValue = "GOOGLE"
)
public class GooglePlaceSearchProvider implements PlaceSearchProvider {

    private static final String FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.location",
            "places.primaryType"
    );

    private final GoogleMapsHttpClient httpClient;
    private final URI endpoint;

    public GooglePlaceSearchProvider(
            GoogleMapsHttpClient httpClient,
            GoogleMapsProperties properties
    ) {
        this.httpClient = httpClient;
        this.endpoint = properties.getPlacesBaseUrl().resolve("/v1/places:searchText");
    }

    @Override
    public List<PlaceSearchResult> search(PlaceSearchQuery query) {
        JsonNode response = httpClient.post(endpoint, FIELD_MASK, requestBody(query));
        JsonNode places = response.get("places");
        if (places == null) {
            return List.of();
        }
        if (!places.isArray()) {
            throw invalidResponse("Places 검색 결과가 배열이 아닙니다.");
        }

        List<PlaceSearchResult> results = new ArrayList<>();
        for (JsonNode place : places) {
            JsonNode location = place.path("location");
            results.add(new PlaceSearchResult(
                    requiredText(place, "id"),
                    requiredText(place.path("displayName"), "text"),
                    optionalText(place, "formattedAddress"),
                    requiredDecimal(location, "latitude"),
                    requiredDecimal(location, "longitude"),
                    optionalText(place, "primaryType"),
                    "GOOGLE_PLACES"
            ));
        }
        return List.copyOf(results);
    }

    private Map<String, Object> requestBody(PlaceSearchQuery query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("textQuery", query.textQuery());
        body.put("pageSize", query.limit());
        body.put("languageCode", query.languageCode());
        if (query.locationBias() != null) {
            body.put("locationBias", Map.of("circle", Map.of(
                    "center", Map.of(
                            "latitude", query.locationBias().latitude(),
                            "longitude", query.locationBias().longitude()
                    ),
                    "radius", query.radiusMeters()
            )));
        }
        return body;
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw invalidResponse("Places 응답 필드가 누락됐습니다: " + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank()
                ? null
                : value.asText();
    }

    private BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw invalidResponse("Places 좌표가 누락됐습니다: " + field);
        }
        return value.decimalValue();
    }

    private ExternalProviderException invalidResponse(String message) {
        return new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message);
    }
}
