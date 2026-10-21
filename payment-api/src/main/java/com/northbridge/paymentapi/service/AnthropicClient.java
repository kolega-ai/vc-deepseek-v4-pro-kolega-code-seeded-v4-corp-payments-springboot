package com.northbridge.paymentapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin REST wrapper for the Claude Messages API. There is no vendor SDK on the classpath, so the
 * request body mirrors the HTTP JSON shape directly and only the fields the payments desk uses are
 * sent. Returns the first text block of the reply, or "" when the call cannot be completed.
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public AnthropicClient(@Value("${app.assistant.messages-base-url:https://api.anthropic.com}") String baseUrl,
                           @Value("${app.assistant.messages-api-key:}") String apiKey,
                           @Value("${app.assistant.model:claude-3-5-sonnet-latest}") String model,
                           RestClient.Builder builder) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Messages messages() {
        return new Messages();
    }

    public final class Messages {

        public String create(String system, String userMessage) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", 512);
            body.put("system", system);
            body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));
            try {
                String raw = restClient.post()
                        .uri("/v1/messages")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                JsonNode content = objectMapper.readTree(raw).path("content");
                if (content.isArray() && content.size() > 0) {
                    return content.get(0).path("text").asText("");
                }
            } catch (Exception e) {
                log.warn("assistant message call failed: {}", e.getMessage());
            }
            return "";
        }
    }
}
