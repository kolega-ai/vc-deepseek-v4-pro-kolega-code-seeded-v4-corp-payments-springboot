package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.security.TokenCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the vendor core-banking stub (core-mock).
 *
 * <p>Every call presents the core credential AND a short-lived signed scope token minted here.
 * The scope token carries the resolved company id and the account numbers this operation may
 * touch; core-mock verifies the signature and refuses any posting for an account outside that
 * scope. core-mock never trusts a raw company or account-owner parameter.</p>
 */
@Component
public class CoreClient {

    private static final Logger log = LoggerFactory.getLogger(CoreClient.class);

    private final TokenCodec tokenCodec;
    private final RestClient restClient;
    private final String coreApiKey;

    public CoreClient(TokenCodec tokenCodec,
                      @Value("${app.core.base-url}") String coreBaseUrl,
                      @Value("${app.core.api-key}") String coreApiKey,
                      RestClient.Builder builder) {
        this.tokenCodec = tokenCodec;
        if (coreApiKey == null || coreApiKey.isBlank()) {
            throw new IllegalStateException("Missing CORE_MOCK_API_KEY. The core-banking credential is "
                    + "read from the environment only and this tier refuses to start without it.");
        }
        this.coreApiKey = coreApiKey;
        this.restClient = builder.baseUrl(coreBaseUrl).build();
    }

    private String companyScopeToken(UUID companyId, List<String> accountNumbers) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("type", "company");
        scope.put("companyId", companyId.toString());
        scope.put("accounts", accountNumbers);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("scope", scope);
        return tokenCodec.mintCoreScopeToken(claims, 30);
    }

    private String bankScopeToken() {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("type", "bank");
        scope.put("op", "postings");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("scope", scope);
        return tokenCodec.mintCoreScopeToken(claims, 30);
    }

    public record Balance(String accountNumber, String currency, java.math.BigDecimal balance) {
    }

    public record PostingResult(String coreRef, String status, String responseCode,
                                java.math.BigDecimal balance, boolean duplicate) {
        public boolean posted() {
            return "POSTED".equals(status);
        }
    }

    public Balance balance(String accountNumber, UUID companyId) {
        String token = companyScopeToken(companyId, List.of(accountNumber));
        return restClient.get()
                .uri("/v1/accounts/{number}/balance", accountNumber)
                .header("X-Core-Api-Key", coreApiKey)
                .header("X-Scope-Token", token)
                .retrieve()
                .body(Balance.class);
    }

    /**
     * Posts a debit/credit to core. Idempotent per requestRef: core-mock returns the original
     * posting for a duplicate requestRef and never posts twice.
     */
    public PostingResult post(String requestRef, String accountNumber, String postingType,
                              java.math.BigDecimal amount, String currency, String counterparty,
                              UUID companyId) {
        String token = companyScopeToken(companyId, List.of(accountNumber));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestRef", requestRef);
        body.put("accountNumber", accountNumber);
        body.put("postingType", postingType);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("counterparty", counterparty);
        try {
            return restClient.post()
                    .uri("/v1/postings")
                    .header("X-Core-Api-Key", coreApiKey)
                    .header("X-Scope-Token", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PostingResult.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("core-mock rejected posting {} for account {}: {}", requestRef, accountNumber,
                    e.getResponseBodyAsString());
            return new PostingResult(null, "CORE_ERROR", String.valueOf(e.getStatusCode().value()),
                    null, false);
        }
    }

    /** Provisions a new account in the vendor core (bank admin action). */
    public void provisionAccount(String accountNumber, String currency, String product,
                                 java.math.BigDecimal openingBalance, UUID companyId) {
        String token = bankScopeToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyId", companyId.toString());
        body.put("accountNumber", accountNumber);
        body.put("currency", currency);
        body.put("product", product);
        body.put("openingBalance", openingBalance);
        try {
            restClient.post()
                    .uri("/internal/accounts")
                    .header("X-Core-Api-Key", coreApiKey)
                    .header("X-Scope-Token", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("core-mock rejected account provisioning {}: {}", accountNumber,
                    e.getResponseBodyAsString());
            throw new IllegalStateException("Core system could not provision account: " + accountNumber);
        }
    }

    /** Lists postings from core for reconciliation (bank scope). */
    public java.util.List<Map<String, Object>> postings(String accountNumber, String since) {
        String token = bankScopeToken();
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/internal/postings");
                        if (accountNumber != null) {
                            b.queryParam("accountNumber", accountNumber);
                        }
                        if (since != null) {
                            b.queryParam("since", since);
                        }
                        return b.build();
                    })
                    .header("X-Core-Api-Key", coreApiKey)
                    .header("X-Scope-Token", token)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() { });
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("core-mock postings listing failed: {}", e.getResponseBodyAsString());
            return java.util.List.of();
        }
    }
}
