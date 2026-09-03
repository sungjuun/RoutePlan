package com.routeplan.contentimport.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.ai.integration.openai.OpenAiHttpClient;
import com.routeplan.ai.integration.openai.OpenAiProperties;
import com.routeplan.contentimport.application.ContentPlaceExtractor;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "routeplan.ai", name = "provider", havingValue = "OPENAI")
public class OpenAiContentPlaceExtractor implements ContentPlaceExtractor {
    private static final String INSTRUCTIONS = """
            You extract concrete real-world place names explicitly mentioned in travel content.
            Never invent or infer a place that is not written in the input. Exclude countries,
            broad cities, generic words, hashtags unrelated to a venue, and duplicate names.
            Preserve the original language and mention order. Return at most 20 place names.
            """;

    private final OpenAiHttpClient httpClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiContentPlaceExtractor(OpenAiHttpClient httpClient, OpenAiProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public List<String> extract(Long userId, String title, String text) {
        if (text == null || text.isBlank()) return List.of();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("instructions", INSTRUCTIONS);
        body.put("input", toJson(Map.of("title", title == null ? "" : title, "content", text)));
        body.put("store", false);
        body.put("max_output_tokens", 500);
        body.put("safety_identifier", "routeplan-import-user-" + userId);
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "routeplan_content_places",
                "strict", true,
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of("placeNames", Map.of(
                                "type", "array", "maxItems", 20,
                                "items", Map.of("type", "string", "minLength", 2, "maxLength", 100)
                        )),
                        "required", List.of("placeNames")
                )
        )));
        try {
            JsonNode parsed = objectMapper.readTree(outputText(httpClient.createResponse(body)));
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (JsonNode name : parsed.path("placeNames")) {
                if (name.isTextual() && !name.asText().isBlank()) unique.add(name.asText().trim());
            }
            return unique.stream().limit(20).toList();
        } catch (JsonProcessingException exception) {
            throw invalid("OpenAI 장소 추출 응답을 해석할 수 없습니다.", exception);
        }
    }

    private String outputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) continue;
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText()) && content.path("text").isTextual()) {
                    return content.path("text").asText();
                }
                if ("refusal".equals(content.path("type").asText())) throw invalid("OpenAI가 장소 추출을 거부했습니다.", null);
            }
        }
        throw invalid("OpenAI 응답에 장소 추출 결과가 없습니다.", null);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("OpenAI 장소 추출 요청을 만들 수 없습니다.", exception);
        }
    }

    private ExternalProviderException invalid(String message, Exception cause) {
        return cause == null
                ? new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message)
                : new ExternalProviderException(ExternalProviderFailure.INVALID_RESPONSE, message, cause);
    }
}
