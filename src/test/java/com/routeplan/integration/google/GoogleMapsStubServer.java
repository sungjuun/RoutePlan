package com.routeplan.integration.google;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

final class GoogleMapsStubServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile Function<RecordedRequest, StubResponse> responder =
            ignored -> new StubResponse(500, "{}");

    GoogleMapsStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI baseUri() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    void respondWith(Function<RecordedRequest, StubResponse> responder) {
        this.responder = responder;
    }

    List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RecordedRequest request = new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(),
                body
        );
        requests.add(request);
        StubResponse response = responder.apply(request);
        byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    record RecordedRequest(
            String method,
            String path,
            Map<String, List<String>> headers,
            String body
    ) {

        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }
    }

    record StubResponse(int status, String body) {
    }
}
