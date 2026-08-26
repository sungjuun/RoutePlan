package com.routeplan.ai.integration.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenAiHttpClient {

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiHttpClient(OpenAiProperties properties) {
        this(
                properties,
                new ObjectMapper().findAndRegisterModules(),
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build()
        );
    }

    OpenAiHttpClient(
            OpenAiProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode createResponse(Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            properties.getBaseUrl().resolve("/v1/responses")
                    )
                    .timeout(properties.getRequestTimeout())
                    .header("Authorization", "Bearer " + properties.requireApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            validateStatus(response.statusCode());
            JsonNode json = objectMapper.readTree(response.body());
            if (json == null || !json.isObject()) {
                throw invalidResponse("OpenAI API가 빈 JSON 응답을 반환했습니다.");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.INVALID_RESPONSE,
                    "OpenAI API JSON 처리에 실패했습니다.",
                    exception
            );
        } catch (java.net.http.HttpTimeoutException exception) {
            throw unavailable("OpenAI API 요청 시간이 초과됐습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("OpenAI API 요청이 중단됐습니다.", exception);
        } catch (IOException exception) {
            throw unavailable("OpenAI API에 연결할 수 없습니다.", exception);
        }
    }

    private void validateStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        if (statusCode == 429) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.RATE_LIMITED,
                    "OpenAI API 요청 한도를 초과했습니다."
            );
        }
        throw new ExternalProviderException(
                ExternalProviderFailure.UNAVAILABLE,
                "OpenAI API가 오류를 반환했습니다: HTTP " + statusCode
        );
    }

    private ExternalProviderException unavailable(String message, Exception cause) {
        return new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, message, cause);
    }

    private ExternalProviderException invalidResponse(String message) {
        return new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message);
    }
}
