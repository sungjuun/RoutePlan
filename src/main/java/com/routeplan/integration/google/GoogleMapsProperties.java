package com.routeplan.integration.google;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.google")
public class GoogleMapsProperties {

    private String apiKey = "";
    private URI placesBaseUrl = URI.create("https://places.googleapis.com");
    private URI routesBaseUrl = URI.create("https://routes.googleapis.com");
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(8);

    public String requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.NOT_CONFIGURED,
                    "Google Maps API 키가 설정되지 않았습니다."
            );
        }
        return apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public URI getPlacesBaseUrl() {
        return placesBaseUrl;
    }

    public void setPlacesBaseUrl(URI placesBaseUrl) {
        this.placesBaseUrl = placesBaseUrl;
    }

    public URI getRoutesBaseUrl() {
        return routesBaseUrl;
    }

    public void setRoutesBaseUrl(URI routesBaseUrl) {
        this.routesBaseUrl = routesBaseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
