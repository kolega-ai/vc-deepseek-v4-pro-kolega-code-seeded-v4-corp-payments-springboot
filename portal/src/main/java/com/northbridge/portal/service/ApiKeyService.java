package com.northbridge.portal.service;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.domain.ApiKey;
import com.northbridge.portal.repo.ApiKeyRepository;
import com.northbridge.portal.security.ApiException;
import com.northbridge.portal.security.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Company-scoped API keys for the JSON payment-status endpoint. Only a SHA-256 hash of the key is
 * stored; the clear key is shown exactly once at creation time.
 */
@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final AuthContext auth;
    private final PortalAuditService auditService;
    private final PaymentApiClient paymentApiClient;

    public ApiKeyService(ApiKeyRepository repository, AuthContext auth,
                         PortalAuditService auditService, PaymentApiClient paymentApiClient) {
        this.repository = repository;
        this.auth = auth;
        this.auditService = auditService;
        this.paymentApiClient = paymentApiClient;
    }

    public List<ApiKey> listForCompany() {
        UUID companyId = auth.currentCompanyId();
        if (companyId == null) {
            return List.of();
        }
        return repository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public record CreatedKey(ApiKey apiKey, String clearKey) {
    }

    @Transactional
    public CreatedKey create(String name) {
        if (!"company_admin".equals(auth.requireUser().getRole())) {
            throw new ApiException("FORBIDDEN", "Only company_admin manages API keys");
        }
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new ApiException("VALIDATION", "Key name is required (max 100)");
        }
        String clearKey = "nb_live_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey key = new ApiKey(UUID.randomUUID(), auth.currentCompanyId(), name.strip(),
                clearKey.substring(0, Math.min(clearKey.length(), 20)),
                sha256(clearKey), "ACTIVE", auth.currentUserId());
        repository.save(key);
        auditService.audit("API_KEY_CREATED", "api_key", key.getKeyPrefix(),
                "API key created: " + key.getName());
        return new CreatedKey(key, clearKey);
    }

    @Transactional
    public void revoke(UUID keyId) {
        if (!"company_admin".equals(auth.requireUser().getRole())) {
            throw new ApiException("FORBIDDEN", "Only company_admin manages API keys");
        }
        ApiKey key = repository.findById(keyId)
                .filter(k -> k.getCompanyId().equals(auth.currentCompanyId()))
                .orElseThrow(() -> new ApiException("NOT_FOUND", "API key not found"));
        key.setStatus("REVOKED");
        key.setRevokedAt(Instant.now());
        repository.save(key);
        auditService.audit("API_KEY_REVOKED", "api_key", key.getKeyPrefix(),
                "API key revoked: " + key.getName());
    }

    /**
     * Resolves an API key header to its company scope and performs the privileged lookup against
     * the decision tier, which re-enforces company scope from its own database.
     */
    @Transactional
    public Api.StatusLookup lookupWithApiKey(String rawKey, String paymentRef, UUID companyScope) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new ApiException("UNAUTHORIZED", "X-Api-Key header required");
        }
        ApiKey key = repository.findByKeyHash(sha256(rawKey.strip()))
                .orElseThrow(() -> new ApiException("UNAUTHORIZED", "Invalid API key"));
        if (!"ACTIVE".equals(key.getStatus())) {
            throw new ApiException("UNAUTHORIZED", "API key is revoked");
        }
        key.setLastUsedAt(Instant.now());
        repository.save(key);
        UUID scope = companyScope != null ? companyScope : key.getCompanyId();
        try {
            Api.StatusLookup lookup = paymentApiClient.lookupByRef(scope, paymentRef);
            auditService.auditSystem("api-key:" + key.getKeyPrefix(), "api_key", scope,
                    "API_KEY_PAYMENT_LOOKUP", "payment", paymentRef,
                    "Privileged read via API key (count=1)");
            return lookup;
        } catch (com.northbridge.portal.client.PaymentApiException e) {
            if (e.getStatus() == 404) {
                throw new ApiException("NOT_FOUND", "Payment not found");
            }
            throw new ApiException("INTERNAL", "Decision tier unavailable");
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
