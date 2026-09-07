package com.routeplan.contentimport.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeplan.contentimport.application.ContentImporter;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.integration.google.ExternalProviderException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class YouTubeContentImporter implements ContentImporter {
    private final ContentImportProviderProperties properties;
    private final SocialMetadataClient client;

    public YouTubeContentImporter(ContentImportProviderProperties properties, SocialMetadataClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public boolean supports(ContentSourceType sourceType) { return sourceType == ContentSourceType.YOUTUBE; }

    @Override
    public ImportedContent load(URI sourceUrl, String userProvidedText) {
        if (userProvidedText != null && !userProvidedText.isBlank()) {
            return ImportedContent.content("YouTube에서 가져온 장소", userProvidedText.trim());
        }
        String key = properties.getYoutubeApiKey();
        if (key == null || key.isBlank()) {
            return ImportedContent.awaiting("YouTube 설명 자동 조회에는 YOUTUBE_API_KEY가 필요합니다. 또는 영상 설명이나 장소 목록을 붙여 넣어 주세요.");
        }
        String videoId = videoId(sourceUrl);
        if (videoId == null) return ImportedContent.awaiting("지원되는 YouTube 영상 또는 Shorts URL과 장소 설명을 입력해 주세요.");
        URI endpoint = URI.create(properties.getYoutubeApiBaseUrl().toString().replaceAll("/$", "")
                + "/videos?part=snippet&id=" + encode(videoId) + "&key=" + encode(key));
        JsonNode item;
        try {
            item = client.get(endpoint).path("items").path(0);
        } catch (ExternalProviderException exception) {
            return ImportedContent.awaiting("YouTube 정보를 자동 조회하지 못했습니다. 영상 설명이나 장소 목록을 붙여 넣어 계속할 수 있습니다.");
        }
        if (item.isMissingNode()) return ImportedContent.awaiting("공개된 YouTube 영상 정보를 찾지 못했습니다. 영상 설명이나 장소 목록을 붙여 넣어 주세요.");
        JsonNode snippet = item.path("snippet");
        String title = snippet.path("title").asText("").strip();
        List<String> parts = new ArrayList<>();
        if (!title.isBlank()) parts.add(title);
        String description = snippet.path("description").asText("").strip();
        if (!description.isBlank()) parts.add(description);
        if (snippet.path("tags").isArray()) snippet.path("tags").forEach(tag -> parts.add("#" + tag.asText()));
        if (parts.isEmpty()) return ImportedContent.awaiting("영상에서 분석할 설명을 찾지 못했습니다. 장소 목록을 붙여 넣어 주세요.");
        return ImportedContent.content(title.isBlank() ? "YouTube 영상" : title, String.join("\n", parts));
    }

    static String videoId(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String candidate = null;
        if (host.equals("youtu.be") || host.endsWith(".youtu.be")) {
            candidate = firstPathSegment(uri.getPath());
        } else {
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.startsWith("/shorts/") || path.startsWith("/embed/")) candidate = path.split("/")[2];
            if (candidate == null && uri.getRawQuery() != null) {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String[] pair = parameter.split("=", 2);
                    if (pair.length == 2 && pair[0].equals("v")) candidate = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                }
            }
        }
        return candidate != null && candidate.matches("[A-Za-z0-9_-]{6,32}") ? candidate : null;
    }

    private static String firstPathSegment(String path) {
        if (path == null) return null;
        return java.util.Arrays.stream(path.split("/")).filter(value -> !value.isBlank()).findFirst().orElse(null);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
