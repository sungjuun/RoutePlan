package com.routeplan.contentimport.integration;

import com.routeplan.contentimport.application.ContentImporter;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GenericWebContentImporter implements ContentImporter {
    static final int MAX_BYTES = 512 * 1024;
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern DESCRIPTION = Pattern.compile(
            "(?is)<meta[^>]+(?:name|property)\\s*=\\s*['\"](?:description|og:description)['\"][^>]+content\\s*=\\s*['\"](.*?)['\"][^>]*>"
    );
    private static final Set<ContentSourceType> TYPES = Set.of(
            ContentSourceType.GENERIC_WEB, ContentSourceType.YOUTUBE,
            ContentSourceType.TIKTOK, ContentSourceType.BLOG, ContentSourceType.COMMUNITY
    );

    private final WebUrlSecurityPolicy securityPolicy;
    private final HttpClient httpClient;

    public GenericWebContentImporter(WebUrlSecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean supports(ContentSourceType sourceType) {
        return TYPES.contains(sourceType);
    }

    @Override
    public ImportedContent load(URI sourceUrl, String userProvidedText) {
        if (userProvidedText != null && !userProvidedText.isBlank()) {
            return ImportedContent.content(sourceUrl.getHost(), userProvidedText.trim());
        }
        securityPolicy.requirePublicHttpUrl(sourceUrl);
        HttpRequest request = HttpRequest.newBuilder(sourceUrl)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", "RoutePlan/2.0 content-import")
                .GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable("웹 페이지가 HTTP " + response.statusCode() + " 응답을 반환했습니다.");
            }
            String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (!type.contains("text/html") && !type.contains("application/xhtml+xml")) {
                throw invalid("HTML 문서만 가져올 수 있습니다.");
            }
            try (InputStream stream = response.body()) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw invalid("가져올 문서가 512KB 제한을 초과했습니다.");
                String html = new String(bytes, StandardCharsets.UTF_8);
                String title = extract(TITLE, html);
                String description = extract(DESCRIPTION, html);
                String text = stripHtml(html);
                String combined = String.join("\n", java.util.stream.Stream.of(title, description, text)
                        .filter(value -> value != null && !value.isBlank()).toList());
                if (combined.length() > 10_000) combined = combined.substring(0, 10_000);
                return ImportedContent.content(title, combined);
            }
        } catch (java.net.http.HttpTimeoutException exception) {
            throw unavailable("웹 페이지 가져오기 시간이 초과됐습니다.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("웹 페이지 가져오기가 중단됐습니다.", exception);
        } catch (IOException exception) {
            throw unavailable("웹 페이지에 연결할 수 없습니다.", exception);
        }
    }

    private String extract(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? decode(matcher.group(1)).trim() : null;
    }

    private String stripHtml(String html) {
        String withoutNoise = html.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ");
        return decode(withoutNoise.replaceAll("(?is)<[^>]+>", "\n"))
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String decode(String value) {
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
    }

    private ExternalProviderException unavailable(String message) {
        return new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, message);
    }
    private ExternalProviderException unavailable(String message, Exception cause) {
        return new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE, message, cause);
    }
    private ExternalProviderException invalid(String message) {
        return new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message);
    }
}
