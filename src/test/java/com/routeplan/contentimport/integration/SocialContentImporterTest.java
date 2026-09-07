package com.routeplan.contentimport.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.contentimport.application.ContentImporter.ImportedContent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SocialContentImporterTest {

    @Test
    void loadsYouTubeSnippetThroughTheOfficialDataApi() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        try (StubServer server = new StubServer("/youtube/videos", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, """
                    {"items":[{"snippet":{"title":"도쿄 카페 여행","description":"Fuglen Tokyo\\nSHIBUYA SKY","tags":["도쿄맛집"]}}]}
                    """);
        })) {
            ContentImportProviderProperties properties = properties(server.uri("/youtube"));
            properties.setYoutubeApiKey("test-youtube-key");
            YouTubeContentImporter importer = new YouTubeContentImporter(properties,
                    new SocialMetadataClient(properties, new ObjectMapper()));

            ImportedContent content = importer.load(URI.create("https://youtu.be/AbCdEf12345"), null);

            assertThat(content.requiresUserInput()).isFalse();
            assertThat(content.title()).isEqualTo("도쿄 카페 여행");
            assertThat(content.text()).contains("Fuglen Tokyo", "SHIBUYA SKY", "#도쿄맛집");
            assertThat(query.get()).contains("part=snippet", "id=AbCdEf12345", "key=test-youtube-key");
        }
    }

    @Test
    void asksForTextWhenYouTubeApiIsNotConfigured() {
        ContentImportProviderProperties properties = new ContentImportProviderProperties();
        YouTubeContentImporter importer = new YouTubeContentImporter(properties,
                new SocialMetadataClient(properties, new ObjectMapper()));

        ImportedContent content = importer.load(URI.create("https://youtube.com/shorts/AbCdEf12345"), null);

        assertThat(content.requiresUserInput()).isTrue();
        assertThat(content.warning()).contains("YOUTUBE_API_KEY");
    }

    @Test
    void loadsTikTokCaptionThroughTheOfficialOembedApi() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        try (StubServer server = new StubServer("/oembed", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, """
                    {"title":"오사카성 다음 도톤보리 #오사카여행","author_name":"traveler"}
                    """);
        })) {
            ContentImportProviderProperties properties = properties(server.uri("/youtube"));
            properties.setTiktokOembedBaseUrl(server.uri("/oembed"));
            TikTokContentImporter importer = new TikTokContentImporter(properties,
                    new SocialMetadataClient(properties, new ObjectMapper()));

            ImportedContent content = importer.load(
                    URI.create("https://www.tiktok.com/@route/video/123456789"), null);

            assertThat(content.requiresUserInput()).isFalse();
            assertThat(content.text()).contains("오사카성", "도톤보리");
            assertThat(query.get()).contains("url=https%3A%2F%2Fwww.tiktok.com%2F%40route%2Fvideo%2F123456789");
        }
    }

    @Test
    void usesUserProvidedTextWithoutCallingAPlatform() {
        ContentImportProviderProperties properties = new ContentImportProviderProperties();
        YouTubeContentImporter youtube = new YouTubeContentImporter(properties,
                new SocialMetadataClient(properties, new ObjectMapper()));
        TikTokContentImporter tiktok = new TikTokContentImporter(properties,
                new SocialMetadataClient(properties, new ObjectMapper()));

        assertThat(youtube.load(URI.create("https://youtu.be/AbCdEf12345"), "경복궁").text()).isEqualTo("경복궁");
        assertThat(tiktok.load(URI.create("https://tiktok.com/@a/video/1"), "도톤보리").text()).isEqualTo("도톤보리");
    }

    @Test
    void platformFailureFallsBackToUserInputInsteadOfFailingTheJob() throws Exception {
        try (StubServer server = new StubServer("/youtube/videos", exchange -> respond(exchange, 429, "{}"))) {
            ContentImportProviderProperties properties = properties(server.uri("/youtube"));
            properties.setYoutubeApiKey("test-youtube-key");
            YouTubeContentImporter importer = new YouTubeContentImporter(properties,
                    new SocialMetadataClient(properties, new ObjectMapper()));

            ImportedContent content = importer.load(URI.create("https://youtu.be/AbCdEf12345"), null);

            assertThat(content.requiresUserInput()).isTrue();
            assertThat(content.warning()).contains("장소 목록");
        }
    }

    private static ContentImportProviderProperties properties(URI youtubeBase) {
        ContentImportProviderProperties properties = new ContentImportProviderProperties();
        properties.setYoutubeApiBaseUrl(youtubeBase);
        properties.setConnectTimeout(java.time.Duration.ofSeconds(1));
        properties.setRequestTimeout(java.time.Duration.ofSeconds(2));
        return properties;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;

        private StubServer(String path, com.sun.net.httpserver.HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, handler);
            server.start();
        }

        private URI uri(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }
        @Override public void close() { server.stop(0); }
    }
}
