package com.routeplan.integration.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class GoogleMapsHttpClient {

    private final GoogleMapsProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GoogleMapsHttpClient(GoogleMapsProperties properties) {
        this(
                properties,
                new ObjectMapper(),
                HttpClient.newBuilder()
                        .connectTimeout(properties.getConnectTimeout())
                        .build()
        );
    }

    GoogleMapsHttpClient(
            GoogleMapsProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode post(URI uri, String fieldMask, Object body) {
        Objects.requireNonNull(uri, "외부 API URI는 필수입니다.");
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.getRequestTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", properties.requireApiKey())
                    .header("X-Goog-FieldMask", fieldMask)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            validateStatus(response.statusCode());
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.INVALID_RESPONSE,
                    "Google Maps API JSON 처리에 실패했습니다.",
                    exception
            );
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.UNAVAILABLE,
                    "Google Maps API 요청 시간이 초과됐습니다.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalProviderException(
                    ExternalProviderFailure.UNAVAILABLE,
                    "Google Maps API 요청이 중단됐습니다.",
                    exception
            );
        } catch (IOException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.UNAVAILABLE,
                    "Google Maps API에 연결할 수 없습니다.",
                    exception
            );
        }
    }

    private void validateStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        if (statusCode == 429) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.RATE_LIMITED,
                    "Google Maps API 요청 한도를 초과했습니다."
            );
        }
        throw new ExternalProviderException(
                ExternalProviderFailure.UNAVAILABLE,
                "Google Maps API가 오류를 반환했습니다: HTTP " + statusCode
        );
    }
}
