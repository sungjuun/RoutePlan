package com.routeplan.contentimport.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeplan.contentimport.application.ContentImporter;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.integration.google.ExternalProviderException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class TikTokContentImporter implements ContentImporter {
    private final ContentImportProviderProperties properties;
    private final SocialMetadataClient client;

    public TikTokContentImporter(ContentImportProviderProperties properties, SocialMetadataClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public boolean supports(ContentSourceType sourceType) { return sourceType == ContentSourceType.TIKTOK; }

    @Override
    public ImportedContent load(URI sourceUrl, String userProvidedText) {
        if (userProvidedText != null && !userProvidedText.isBlank()) {
            return ImportedContent.content("TikTok에서 가져온 장소", userProvidedText.trim());
        }
        URI endpoint = URI.create(properties.getTiktokOembedBaseUrl() + "?url="
                + URLEncoder.encode(sourceUrl.toString(), StandardCharsets.UTF_8));
        JsonNode response;
        try {
            response = client.get(endpoint);
        } catch (ExternalProviderException exception) {
            return ImportedContent.awaiting("TikTok 정보를 자동 조회하지 못했습니다. 캡션이나 장소 목록을 붙여 넣어 계속할 수 있습니다.");
        }
        String title = response.path("title").asText("").strip();
        if (title.isBlank()) return ImportedContent.awaiting("TikTok 설명을 가져오지 못했습니다. 캡션이나 장소 목록을 붙여 넣어 주세요.");
        return ImportedContent.content(title, title);
    }
}
