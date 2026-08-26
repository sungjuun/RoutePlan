package com.routeplan.ai.integration.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.ai.application.TravelConstraintInterpreter;
import com.routeplan.ai.application.TravelInterpretationContext;
import com.routeplan.ai.domain.TravelConstraints;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "routeplan.ai", name = "provider", havingValue = "OPENAI")
public class OpenAiTravelConstraintInterpreter implements TravelConstraintInterpreter {

    private static final String INSTRUCTIONS = """
            You extract travel preferences into the supplied JSON schema.
            Extract only conditions explicitly stated by the user; use null or an empty array otherwise.
            Use a known place name exactly as supplied and never invent a place or ID.
            MUST_VISIT means the user explicitly said the place is mandatory.
            Do not calculate distance, travel time, visit order, opening hours, feasibility, or scores.
            Put unsupported or ambiguous requests in notes. Keep notes concise and write them in Korean.
            """;

    private final OpenAiHttpClient httpClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiTravelConstraintInterpreter(
            OpenAiHttpClient httpClient,
            OpenAiProperties properties
    ) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public String providerName() {
        return "OPENAI:" + properties.getModel();
    }

    @Override
    public TravelConstraints interpret(TravelInterpretationContext context) {
        JsonNode response = httpClient.createResponse(requestBody(context));
        if (!"completed".equals(response.path("status").asText())) {
            throw invalidResponse("OpenAI Structured Output 생성이 완료되지 않았습니다.");
        }
        String outputText = findOutputText(response);
        try {
            return objectMapper.treeToValue(objectMapper.readTree(outputText), TravelConstraints.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.INVALID_RESPONSE,
                    "OpenAI Structured Output을 검증할 수 없습니다.",
                    exception
            );
        }
    }

    private Map<String, Object> requestBody(TravelInterpretationContext context) {
        Map<String, Object> currentTrip = new LinkedHashMap<>();
        currentTrip.put("dailyStartTime", context.currentDailyStartTime().toString());
        currentTrip.put("dailyEndTime", context.currentDailyEndTime().toString());
        currentTrip.put("pace", context.currentPace().name());
        currentTrip.put("transportMode", context.currentTransportMode().name());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userRequest", context.userRequest());
        input.put("currentTrip", currentTrip);
        input.put("knownPlaces", context.knownPlaces().stream()
                .map(place -> place.name())
                .toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("instructions", INSTRUCTIONS);
        body.put("input", toJson(input));
        body.put("store", false);
        body.put("max_output_tokens", 1_200);
        body.put("safety_identifier", "routeplan-user-" + context.userId());
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "routeplan_travel_constraints",
                "strict", true,
                "schema", outputSchema()
        )));
        return body;
    }

    private Map<String, Object> outputSchema() {
        Map<String, Object> placeProperties = new LinkedHashMap<>();
        placeProperties.put("placeName", nullableString());
        placeProperties.put("preference", nullableEnum("MUST_VISIT", "PREFERRED", "OPTIONAL"));
        placeProperties.put("preferredStartTime", nullableTime());
        placeProperties.put("preferredEndTime", nullableTime());
        placeProperties.put("minimumStayMinutes", nullableInteger(1, 1_440));
        placeProperties.put("maximumStayMinutes", nullableInteger(1, 1_440));
        placeProperties.put("mealType", nullableEnum("BREAKFAST", "LUNCH", "DINNER"));

        Map<String, Object> placeItem = new LinkedHashMap<>();
        placeItem.put("type", "object");
        placeItem.put("additionalProperties", false);
        placeItem.put("properties", placeProperties);
        placeItem.put("required", List.copyOf(placeProperties.keySet()));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("dailyStartTime", nullableTime());
        properties.put("dailyEndTime", nullableTime());
        properties.put("pace", nullableEnum("ACTIVE", "STANDARD", "RELAXED"));
        properties.put("transportMode", nullableEnum("WALKING", "DRIVING", "PUBLIC_TRANSIT"));
        properties.put("walkingPreference", nullableEnum("LOW", "STANDARD", "HIGH"));
        properties.put("placeConstraints", Map.of("type", "array", "items", placeItem));
        properties.put("notes", Map.of("type", "array", "items", Map.of("type", "string")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }

    private Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }

    private Map<String, Object> nullableTime() {
        return Map.of(
                "type", List.of("string", "null"),
                "pattern", "^([01]\\d|2[0-3]):[0-5]\\d$"
        );
    }

    private Map<String, Object> nullableInteger(int minimum, int maximum) {
        return Map.of(
                "type", List.of("integer", "null"),
                "minimum", minimum,
                "maximum", maximum
        );
    }

    private Map<String, Object> nullableEnum(String... values) {
        List<String> types = List.of("string", "null");
        List<String> enumValues = new java.util.ArrayList<>(Arrays.asList(values));
        enumValues.add(null);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", types);
        schema.put("enum", enumValues);
        return schema;
    }

    private String findOutputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())
                        && content.path("text").isTextual()) {
                    return content.path("text").asText();
                }
                if ("refusal".equals(content.path("type").asText())) {
                    throw invalidResponse("OpenAI가 자연어 요청 해석을 거부했습니다.");
                }
            }
        }
        throw invalidResponse("OpenAI 응답에 Structured Output이 없습니다.");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ExternalProviderException(
                    ExternalProviderFailure.INVALID_RESPONSE,
                    "OpenAI 요청 컨텍스트를 생성할 수 없습니다.",
                    exception
            );
        }
    }

    private ExternalProviderException invalidResponse(String message) {
        return new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message);
    }
}
