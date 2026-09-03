package com.routeplan.contentimport.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.contentimport.domain.ContentSourceType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ContentSourceDetector {

    public DetectedSource detect(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw unsupported();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            ContentSourceType type = switch (normalizedHost(host)) {
                case "instagram.com" -> ContentSourceType.INSTAGRAM;
                case "youtube.com", "youtu.be" -> ContentSourceType.YOUTUBE;
                case "tiktok.com" -> ContentSourceType.TIKTOK;
                case "blog.naver.com", "medium.com" -> ContentSourceType.BLOG;
                default -> ContentSourceType.GENERIC_WEB;
            };
            return new DetectedSource(uri.normalize(), type);
        } catch (URISyntaxException exception) {
            throw unsupported();
        }
    }

    private String normalizedHost(String host) {
        String value = host.startsWith("www.") ? host.substring(4) : host;
        String[] supported = {"instagram.com", "youtube.com", "tiktok.com"};
        for (String root : supported) {
            if (value.equals(root) || value.endsWith("." + root)) return root;
        }
        return value;
    }

    private RoutePlanException unsupported() {
        return new RoutePlanException(ErrorCode.CONTENT_IMPORT_UNSUPPORTED_URL);
    }

    public record DetectedSource(URI uri, ContentSourceType sourceType) {}
}
