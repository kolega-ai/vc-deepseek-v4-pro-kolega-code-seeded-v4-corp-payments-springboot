package com.northbridge.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the payment-api INTERNAL endpoints. Authenticated with the shared internal service
 * credential; the decision tier treats this module as a system actor and derives all scope from
 * its own database.
 */
@Component
public class PaymentApiInternalClient {

    private final RestClient restClient;
    private final String serviceToken;

    public PaymentApiInternalClient(RestClient.Builder builder,
                                    @Value("${app.payment-api.base-url}") String baseUrl,
                                    @Value("${app.security.internal-service-token}") String serviceToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        if (serviceToken == null || serviceToken.isBlank() || serviceToken.length() < 16) {
            throw new IllegalStateException("Missing INTERNAL_SERVICE_TOKEN — the batch module refuses to start");
        }
        this.serviceToken = serviceToken;
    }

    private RestClient.RequestBodySpec post(String path, Object body) {
        return restClient.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body);
    }

    public Map<String, Object> processBulkFiles() {
        return call(() -> post("/internal/bulk-files/process-due", null).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }), "process bulk files");
    }

    public Map<String, Object> processReleases() {
        return call(() -> post("/internal/releases/process-due", null).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }), "process releases");
    }

    public Map<String, Object> processReversals() {
        return call(() -> post("/internal/reversals/process-due", null).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                }), "process reversals");
    }

    public record ReleaseCandidate(UUID paymentId, String paymentRef, String coreRef,
                                   BigDecimal postedAmount, String accountNumber) {
    }

    public List<ReleaseCandidate> reconciliationCandidates() {
        return call(() -> restClient.get().uri("/internal/reconciliation/candidates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .retrieve().body(new ParameterizedTypeReference<List<ReleaseCandidate>>() {
                        }),
                "fetch reconciliation candidates");
    }

    public Map<String, Object> reportReconciliationExceptions(List<Map<String, Object>> exceptions) {
        return call(() -> post("/internal/reconciliation/report", Map.of("exceptions", exceptions))
                .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {
                }), "report reconciliation exceptions");
    }

    public record WebhookDelivery(UUID id, UUID paymentId, String paymentRef, String url,
                                  String status, int attempts, String responseCode,
                                  java.time.Instant createdAt) {
    }

    public List<WebhookDelivery> pendingWebhooks() {
        return call(() -> restClient.get().uri("/internal/webhooks/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .retrieve().body(new ParameterizedTypeReference<List<WebhookDelivery>>() {
                        }),
                "fetch pending webhooks");
    }

    public void recordWebhookDelivery(UUID id, String status, String responseCode) {
        call(() -> post("/internal/webhooks/" + id + "/delivered",
                Map.of("status", status, "responseCode", responseCode)).retrieve().toBodilessEntity(),
                "record webhook delivery");
    }

    private <T> T call(java.util.concurrent.Callable<T> action, String what) {
        try {
            return action.call();
        } catch (Exception e) {
            throw new IllegalStateException("payment-api call failed: " + what + " (" + e.getMessage() + ")", e);
        }
    }
}
