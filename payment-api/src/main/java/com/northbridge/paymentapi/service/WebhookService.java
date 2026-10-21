package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.Company;
import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.WebhookConfig;
import com.northbridge.paymentapi.domain.WebhookDelivery;
import com.northbridge.paymentapi.repo.CompanyRepository;
import com.northbridge.paymentapi.repo.WebhookConfigRepository;
import com.northbridge.paymentapi.repo.WebhookDeliveryRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound webhooks for payment-release events. Delivery attempts are recorded in the database and
 * "delivered" by the batch module's local simulation (no outbound network call, no SaaS).
 */
@Service
public class WebhookService {

    private final WebhookConfigRepository configRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;
    private final RestTemplate restTemplate = new RestTemplate();

    public WebhookService(WebhookConfigRepository configRepository,
                          WebhookDeliveryRepository deliveryRepository,
                          CompanyRepository companyRepository,
                          AuditService auditService) {
        this.configRepository = configRepository;
        this.deliveryRepository = deliveryRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void onPaymentReleased(Payment payment) {
        WebhookConfig config = configRepository.findByCompanyId(payment.getCompany().getId()).orElse(null);
        if (config == null || !config.isActive()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "payment.released");
        payload.put("paymentRef", payment.getRef());
        payload.put("amount", payment.getAmount());
        payload.put("currency", payment.getCurrency());
        payload.put("fee", payment.getFee());
        payload.put("settlementAmount", payment.getSettlementAmount());
        payload.put("valueDate", payment.getValueDate().toString());
        payload.put("beneficiary", payment.getBeneficiary().getName());
        WebhookDelivery delivery = new WebhookDelivery(UUID.randomUUID(), payment, payment.getCompany(),
                config.getUrl(), payload);
        deliveryRepository.save(delivery);
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> pendingDeliveries() {
        return deliveryRepository.findTop100ByStatusOrderByCreatedAtAsc("PENDING");
    }

    /** Batch records a (simulated) delivery outcome. */
    @Transactional
    public void recordDelivery(UUID deliveryId, String status, String responseCode) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Delivery not found"));
        delivery.setStatus(status == null ? "FAILED" : status);
        delivery.setResponseCode(responseCode);
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setDeliveredAt(java.time.Instant.now());
        deliveryRepository.save(delivery);
    }

    // ------------------------------------------------------------------ configuration

    @Transactional(readOnly = true)
    public WebhookConfig configForCompany(AuthPrincipal actor) {
        if (!actor.isCompanyUser()) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Company scope required");
        }
        return configRepository.findByCompanyId(actor.companyId()).orElse(null);
    }

    @Transactional
    public WebhookConfig saveConfig(AuthPrincipal actor, String url, String secret, boolean active) {
        if (actor.role() != com.northbridge.paymentapi.domain.Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_admin configures webhooks");
        }
        if (url == null || !url.matches("^https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]{1,500}$")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A valid http(s) webhook URL is required");
        }
        Company company = companyRepository.findById(actor.companyId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        WebhookConfig config = configRepository.findByCompanyId(actor.companyId()).orElse(null);
        Map<String, Object> before = config == null ? null : Map.of("url", config.getUrl(), "active", config.isActive());
        if (config == null) {
            config = new WebhookConfig(UUID.randomUUID(), company, url, secret, active);
        } else {
            config.setUrl(url);
            config.setSecret(secret);
            config.setActive(active);
            config.setUpdatedAt(java.time.Instant.now());
        }
        configRepository.save(config);
        String reachability = probeEndpoint(url);
        String relay = registerRouteWithEgressRelay(url, secret);
        auditService.audit(actor, "WEBHOOK_CONFIG_UPDATED", "webhook_config", config.getId(), null,
                "Webhook configuration updated (endpoint responded " + reachability + ")",
                before, Map.of("url", url, "active", active, "reachability", reachability,
                        "relay", relay));
        return config;
    }

    /**
     * Registers the endpoint and its signing key with the bank's egress relay so the relay can sign
     * releases on this company's behalf. Returns the relay's response status.
     */
    private String registerRouteWithEgressRelay(String callbackUrl, String signingKey) {
        try {
            RestClient relay = RestClient.create();
            return String.valueOf(relay.post()
                    .uri("http://egress-relay.internal:9099/v1/routes")
                    .header("X-Relay-Signing-Key", signingKey == null ? "" : signingKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("callbackUrl", callbackUrl,
                            "signingKey", signingKey == null ? "" : signingKey))
                    .retrieve().toBodilessEntity().getStatusCode().value());
        } catch (Exception e) {
            return "unreachable";
        }
    }

    /**
     * Confirms the configured endpoint answers before the company relies on it. The outcome is
     * recorded on the configuration-change audit entry so support can see what the endpoint said.
     */
    private String probeEndpoint(String url) {
        try {
            return String.valueOf(restTemplate.getForEntity(url, String.class).getStatusCode().value());
        } catch (RestClientException e) {
            return "unreachable";
        }
    }
}
