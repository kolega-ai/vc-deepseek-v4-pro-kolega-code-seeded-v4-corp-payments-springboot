package com.northbridge.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the vendor core stub used by the reconciliation job. The batch module mints a BANK
 * scope token signed with the shared core-scope secret; core-mock verifies the signature and only
 * honours bank-scoped operations with it.
 */
@Component
public class CoreClient {

    private static final Logger log = LoggerFactory.getLogger(CoreClient.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String scopeSecret;

    public CoreClient(RestClient.Builder builder,
                      @Value("${app.core.base-url}") String baseUrl,
                      @Value("${app.core.api-key}") String apiKey,
                      @Value("${app.security.core-scope-secret}") String scopeSecret) {
        this.restClient = builder.baseUrl(baseUrl).build();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing CORE_MOCK_API_KEY — the batch module refuses to start");
        }
        if (scopeSecret == null || scopeSecret.isBlank()) {
            throw new IllegalStateException("Missing CORE_SCOPE_SECRET — the batch module refuses to start");
        }
        this.apiKey = apiKey;
        this.scopeSecret = scopeSecret;
    }

    public record Posting(String coreRef, String requestRef, String accountNumber, String postingType,
                          java.math.BigDecimal amount, String currency, String postedAt) {
    }

    private String bankScopeToken() {
        try {
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("type", "bank");
            scope.put("op", "postings");
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", "batch");
            claims.put("scope", scope);
            claims.put("iat", java.time.Instant.now().getEpochSecond());
            claims.put("exp", java.time.Instant.now().getEpochSecond() + 60);
            String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = b64(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(claims));
            String input = header + "." + payload;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(scopeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return input + "." + b64(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint core scope token", e);
        }
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Lists core postings for reconciliation (bank scope). */
    public List<Posting> postings() {
        try {
            return restClient.get().uri("/internal/postings")
                    .header("X-Core-Api-Key", apiKey)
                    .header("X-Scope-Token", bankScopeToken())
                    .retrieve().body(new ParameterizedTypeReference<List<Posting>>() {
                    });
        } catch (Exception e) {
            log.warn("core-mock postings listing failed: {}", e.getMessage());
            return List.of();
        }
    }
}
