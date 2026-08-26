package com.routeplan.ai.integration.openai;

import static com.routeplan.integration.retry.ExternalRetryTestSupport.noDelayRetryExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.ai.application.TravelInterpretationContext;
import com.routeplan.ai.domain.MealType;
import com.routeplan.ai.domain.PlacePreference;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class OpenAiTravelConstraintInterpreterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void requestsStrictStructuredOutputAndParsesValidatedResponse() throws Exception {
        try (StubServer server = new StubServer()) {
            String structuredOutput = """
                    {
                      "dailyStartTime":"10:00",
                      "dailyEndTime":"19:00",
                      "pace":"RELAXED",
                      "transportMode":"PUBLIC_TRANSIT",
                      "walkingPreference":"LOW",
                      "placeConstraints":[{
                        "placeName":"오사카성",
                        "preference":"MUST_VISIT",
                        "preferredStartTime":null,
                        "preferredEndTime":null,
                        "minimumStayMinutes":90,
                        "maximumStayMinutes":120,
                        "mealType":null
                      },{
                        "placeName":"이치란 라멘",
                        "preference":"PREFERRED",
                        "preferredStartTime":null,
                        "preferredEndTime":null,
                        "minimumStayMinutes":null,
                        "maximumStayMinutes":null,
                        "mealType":"LUNCH"
                      }],
                      "notes":[]
                    }
                    """;
            server.respond(200, """
                    {
                      "status":"completed",
                      "output":[{
                        "type":"message",
                        "content":[{"type":"output_text","text":%s}]
                      }]
                    }
                    """.formatted(objectMapper.writeValueAsString(structuredOutput)));
            OpenAiTravelConstraintInterpreter interpreter = interpreter(server, "secret-key");

            var result = interpreter.interpret(context());

            assertThat(result.dailyStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(result.pace()).isEqualTo(TripPace.RELAXED);
            assertThat(result.transportMode()).isEqualTo(TransportMode.PUBLIC_TRANSIT);
            assertThat(result.placeConstraints().getFirst().preference())
                    .isEqualTo(PlacePreference.MUST_VISIT);
            assertThat(result.placeConstraints().get(1).mealType()).isEqualTo(MealType.LUNCH);

            RecordedRequest request = server.requests().getFirst();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/v1/responses");
            assertThat(request.header("Authorization")).isEqualTo("Bearer secret-key");
            JsonNode body = objectMapper.readTree(request.body());
            assertThat(body.path("store").asBoolean()).isFalse();
            assertThat(body.path("model").asText()).isEqualTo("gpt-5.4-mini");
            assertThat(body.path("text").path("format").path("type").asText())
                    .isEqualTo("json_schema");
            assertThat(body.path("text").path("format").path("strict").asBoolean()).isTrue();
            assertThat(body.path("input").asText()).contains("오사카성").doesNotContain("secret-key");
        }
    }

    @Test
    void mapsRateLimitWithoutLeakingApiKey() throws Exception {
        try (StubServer server = new StubServer()) {
            server.respond(429, "{}");
            OpenAiTravelConstraintInterpreter interpreter = interpreter(server, "secret-key");

            assertThatThrownBy(() -> interpreter.interpret(context()))
                    .isInstanceOfSatisfying(ExternalProviderException.class, exception -> {
                        assertThat(exception.failure()).isEqualTo(ExternalProviderFailure.RATE_LIMITED);
                        assertThat(exception.getMessage()).doesNotContain("secret-key");
                    });
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test
    void doesNotRetryNonTransientClientFailure() throws Exception {
        try (StubServer server = new StubServer()) {
            server.respond(400, "{}");
            OpenAiTravelConstraintInterpreter interpreter = interpreter(server, "secret-key");

            assertThatThrownBy(() -> interpreter.interpret(context()))
                    .isInstanceOfSatisfying(ExternalProviderException.class, exception ->
                            assertThat(exception.failure())
                                    .isEqualTo(ExternalProviderFailure.INVALID_RESPONSE));
            assertThat(server.requests()).hasSize(1);
        }
    }

    private OpenAiTravelConstraintInterpreter interpreter(StubServer server, String apiKey) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(server.baseUri());
        return new OpenAiTravelConstraintInterpreter(
                new OpenAiHttpClient(properties, noDelayRetryExecutor(3)),
                properties
        );
    }

    private TravelInterpretationContext context() {
        return new TravelInterpretationContext(
                7L,
                "오사카성은 꼭 가고 점심은 이치란 라멘, 일정은 여유롭게 해줘.",
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                TripPace.STANDARD,
                TransportMode.WALKING,
                List.of(
                        new TravelInterpretationContext.KnownPlace(1L, "오사카성"),
                        new TravelInterpretationContext.KnownPlace(2L, "이치란 라멘")
                )
        );
    }

    private static final class StubServer implements AutoCloseable {

        private final HttpServer server;
        private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
        private volatile int status = 500;
        private volatile String responseBody = "{}";

        private StubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        URI baseUri() {
            return URI.create("http://localhost:" + server.getAddress().getPort());
        }

        void respond(int nextStatus, String body) {
            status = nextStatus;
            responseBody = body;
        }

        List<RecordedRequest> requests() {
            return List.copyOf(requests);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(),
                    body
            ));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record RecordedRequest(
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
}
