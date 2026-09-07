package com.routeplan.contentimport.integration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "routeplan.content-import")
public class ContentImportProviderProperties {
    private String youtubeApiKey = "";
    private URI youtubeApiBaseUrl = URI.create("https://www.googleapis.com/youtube/v3");
    private URI tiktokOembedBaseUrl = URI.create("https://www.tiktok.com/oembed");
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(8);

    public String getYoutubeApiKey() { return youtubeApiKey; }
    public void setYoutubeApiKey(String youtubeApiKey) { this.youtubeApiKey = youtubeApiKey; }
    public URI getYoutubeApiBaseUrl() { return youtubeApiBaseUrl; }
    public void setYoutubeApiBaseUrl(URI youtubeApiBaseUrl) { this.youtubeApiBaseUrl = youtubeApiBaseUrl; }
    public URI getTiktokOembedBaseUrl() { return tiktokOembedBaseUrl; }
    public void setTiktokOembedBaseUrl(URI tiktokOembedBaseUrl) { this.tiktokOembedBaseUrl = tiktokOembedBaseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
}
