package com.routeplan.ai.integration.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.integration.ExternalUsageGuard;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.integration.retry.ExternalApiOperation;
import com.routeplan.integration.retry.ExternalRetryExecutor;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenAiHttpClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiHttpClient.class);

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExternalRetryExecutor retryExecutor;
    private final ExternalUsageGuard usageGuard;

    @Autowired
    public OpenAiHttpClient(
            OpenAiProperties properties,
            ExternalRetryExecutor retryExecutor,
            ExternalUsageGuard usageGuard
    ) {
        this(
                properties,
                new ObjectMapper().findAndRegisterModules(),
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(),
                retryExecutor,
                usageGuard
        );
    }

    OpenAiHttpClient(OpenAiProperties properties, ExternalRetryExecutor retryExecutor) {
        this(properties, new ObjectMapper().findAndRegisterModules(),
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(),
                retryExecutor, null);
    }

    OpenAiHttpClient(
            OpenAiProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            ExternalRetryExecutor retryExecutor,
            ExternalUsageGuard usageGuard
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.retryExecutor = retryExecutor;
        this.usageGuard = usageGuard;
    }

    public JsonNode createResponse(Object body) {
        HttpRequest request = request(body);
        return retryExecutor.execute(
                ExternalApiOperation.OPENAI_RESPONSES,
                () -> tracked(request)
        );
    }

    private JsonNode tracked(HttpRequest request) {
        if (usageGuard != null) usageGuard.reserve(ExternalApiOperation.OPENAI_RESPONSES, 1);
        long started = System.nanoTime();
        try {
            JsonNode response = send(request);
            recordOutcome(true, elapsedMillis(started));
            recordTokens(response);
            return response;
        } catch (RuntimeException exception) {
            recordOutcome(false, elapsedMillis(started));
            throw exception;
        }
    }

    private void recordOutcome(boolean success, long latencyMs) {
        if (usageGuard == null) return;
        try {
            usageGuard.recordOutcome(ExternalApiOperation.OPENAI_RESPONSES, 1, success, latencyMs);
        } catch (RuntimeException exception) {
            log.warn("OpenAI API 결과 계측 저장에 실패했습니다.");
        }
    }

    private void recordTokens(JsonNode response) {
        if (usageGuard == null || !response.path("usage").isObject()) return;
        long input = response.path("usage").path("input_tokens").asLong(-1);
        long output = response.path("usage").path("output_tokens").asLong(-1);
        if (input < 0 || output < 0) return;
        try {
            usageGuard.recordOpenAiTokens(input, output);
        } catch (RuntimeException exception) {
            log.warn("OpenAI API 토큰 계측 저장에 실패했습니다.");
        }
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private HttpRequest request(Object body) {
        try {
            return HttpRequest.newBuilder(
                            properties.getBaseUrl().resolve("/v1/responses")
                    )
                    .timeout(properties.getRequestTimeout())
                    .header("Authorization", "Bearer " + properties.requireApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (JsonProcessingException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.INVALID_RESPONSE,
                    "OpenAI API JSON 처리에 실패했습니다.",
                    exception
            );
        }
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
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
        if (statusCode == 408 || (statusCode >= 500 && statusCode < 600)) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.UNAVAILABLE,
                    "OpenAI API가 일시적 오류를 반환했습니다: HTTP " + statusCode
            );
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.NOT_CONFIGURED,
                    "OpenAI API 인증에 실패했습니다: HTTP " + statusCode
            );
        }
        throw new ExternalProviderException(
                ExternalProviderFailure.INVALID_RESPONSE,
                "OpenAI API가 요청을 거부했습니다: HTTP " + statusCode
        );
    }

    private ExternalProviderException unavailable(String message, Exception cause) {
        return new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, message, cause);
    }

    private ExternalProviderException invalidResponse(String message) {
        return new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message);
    }
}
