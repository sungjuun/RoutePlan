package com.routeplan.contentimport.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SocialMetadataClient {
    private static final int MAX_BYTES = 128 * 1024;
    private final ContentImportProviderProperties properties;
    private final HttpClient client;
    private final ObjectMapper json;

    @Autowired
    public SocialMetadataClient(ContentImportProviderProperties properties) {
        this(properties, new ObjectMapper(), HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build());
    }

    SocialMetadataClient(ContentImportProviderProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build());
    }

    SocialMetadataClient(ContentImportProviderProperties properties, ObjectMapper json, HttpClient client) {
        this.properties = properties;
        this.json = json;
        this.client = client;
    }

    public JsonNode get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", "RoutePlan/2.0 content-import")
                .GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 429) throw failure(ExternalProviderFailure.RATE_LIMITED, "SNS 메타데이터 요청 한도를 초과했습니다.");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure(ExternalProviderFailure.UNAVAILABLE, "SNS 게시물 정보를 가져올 수 없습니다.");
            }
            try (InputStream stream = response.body()) {
                byte[] body = stream.readNBytes(MAX_BYTES + 1);
                if (body.length > MAX_BYTES) throw failure(ExternalProviderFailure.INVALID_RESPONSE, "SNS 응답 크기가 제한을 초과했습니다.");
                return json.readTree(body);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, "SNS 게시물 조회 시간이 초과됐습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, "SNS 게시물 조회가 중단됐습니다.", exception);
        } catch (IOException exception) {
            throw new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, "SNS 게시물에 연결할 수 없습니다.", exception);
        }
    }

    private ExternalProviderException failure(ExternalProviderFailure failure, String message) {
        return new ExternalProviderException(failure, message);
    }
}
