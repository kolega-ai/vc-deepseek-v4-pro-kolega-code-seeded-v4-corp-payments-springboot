package com.northbridge.portal.client;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the payment-api decision tier.
 *
 * <p>Every call presents an opaque, signed session token minted from the authenticated portal
 * session. payment-api verifies the signature and resolves the acting user, role, company scope
 * and approval limit from ITS OWN database on every request — the portal never sends any
 * identity/role/company/limit value as a header or field, and payment-api would ignore it anyway.</p>
 *
 * <p>Internal endpoints (API-key proxy, invite activation) use the shared service credential.</p>
 */
@Component
public class PaymentApiClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentApiClient.class);

    private final RestClient restClient;
    private final String tierSecret;
    private final String internalServiceToken;

    public PaymentApiClient(RestClient.Builder builder,
                            @Value("${app.payment-api.base-url}") String baseUrl,
                            @Value("${app.security.tier-token-secret}") String tierSecret,
                            @Value("${app.security.internal-service-token}") String internalServiceToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.tierSecret = require(tierSecret, "TIER_TOKEN_SECRET");
        this.internalServiceToken = require(internalServiceToken, "INTERNAL_SERVICE_TOKEN");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank() || value.length() < 16) {
            throw new IllegalStateException("Missing or too-short " + name
                    + ". The portal refuses to start without it (see .env.example / README).");
        }
        return value;
    }

    /** Mints the short-lived signed session token for one payment-api call. */
    public String sessionToken(UUID userId, String sessionId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("sid", sessionId);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().getEpochSecond() + 600);
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        try {
            String payload = b64(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsBytes(claims));
            String input = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tierSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return input + "." + b64(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint session token", e);
        }
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private RestClient.RequestHeadersSpec<?> sessionGet(String path, String token) {
        return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private RestClient.RequestBodySpec sessionPost(String path, String token, Object body) {
        return restClient.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body);
    }

    private RestClient.RequestBodySpec sessionPut(String path, String token, Object body) {
        return restClient.put().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body);
    }

    private void sessionDelete(String path, String token) {
        try {
            restClient.delete().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private <T> T get(String token, String path, Class<T> type) {
        try {
            return sessionGet(path, token).retrieve().body(type);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private <T> T get(String token, String path, ParameterizedTypeReference<T> type) {
        try {
            return sessionGet(path, token).retrieve().body(type);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private <T> T getList(String token, String path, ParameterizedTypeReference<T> type) {
        try {
            return sessionGet(path, token).retrieve().body(type);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private <T> T post(String token, String path, Object body, Class<T> type) {
        try {
            return sessionPost(path, token, body).retrieve().body(type);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private <T> T post(String token, String path, Object body, ParameterizedTypeReference<T> type) {
        try {
            return sessionPost(path, token, body).retrieve().body(type);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private void post(String token, String path, Object body) {
        try {
            sessionPost(path, token, body).retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private <T> T put(String token, String path, Object body, Class<T> type) {
        try {
            return sessionPut(path, token, body).retrieve().body(type);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    private PaymentApiException translate(org.springframework.web.client.HttpStatusCodeException e) {
        Api.ErrorBody body = null;
        try {
            body = e.getResponseBodyAs(Api.ErrorBody.class);
        } catch (Exception ignored) {
            // non-JSON error
        }
        return new PaymentApiException(e.getStatusCode(), body);
    }

    // ------------------------------------------------------------------ profile / dashboards

    public Api.UserProfile me(String token) {
        return get(token, "/api/me", Api.UserProfile.class);
    }

    public Api.Dashboard dashboard(String token) {
        return get(token, "/api/dashboard", Api.Dashboard.class);
    }

    public Api.OpsDashboard opsDashboard(String token) {
        return get(token, "/api/ops/dashboard", Api.OpsDashboard.class);
    }

    public Api.AdminDashboard adminDashboard(String token) {
        return get(token, "/api/admin/dashboard", Api.AdminDashboard.class);
    }

    // ------------------------------------------------------------------ payments

    public Api.PageOfPayments searchPayments(String token, Map<String, Object> params) {
        StringBuilder uri = new StringBuilder("/api/payments?");
        params.forEach((k, v) -> {
            if (v != null && !String.valueOf(v).isBlank()) {
                uri.append(k).append('=').append(String.valueOf(v)).append('&');
            }
        });
        try {
            return sessionGet(uri.toString(), token).retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    public Api.PaymentDetail payment(String token, UUID id) {
        return get(token, "/api/payments/" + id, Api.PaymentDetail.class);
    }

    public Api.PaymentSummary createPayment(String token, Map<String, Object> body) {
        return post(token, "/api/payments", body, Api.PaymentSummary.class);
    }

    public Api.FeePreview preview(String token, Map<String, Object> body) {
        return post(token, "/api/payments/preview", body, Api.FeePreview.class);
    }

    public Api.PaymentSummary paymentAction(String token, UUID id, String action, Map<String, Object> body) {
        return post(token, "/api/payments/" + id + "/" + action, body, Api.PaymentSummary.class);
    }

    public List<Api.Comment> addComment(String token, UUID id, String text) {
        return post(token, "/api/payments/" + id + "/comments", Map.of("text", text),
                new ParameterizedTypeReference<List<Api.Comment>>() {
                });
    }

    // ------------------------------------------------------------------ beneficiaries / accounts

    public List<Api.Beneficiary> beneficiaries(String token) {
        return getList(token, "/api/beneficiaries", new ParameterizedTypeReference<List<Api.Beneficiary>>() {
        });
    }

    public Api.Beneficiary createBeneficiary(String token, Map<String, Object> body) {
        return post(token, "/api/beneficiaries", body, Api.Beneficiary.class);
    }

    public Api.Beneficiary updateBeneficiary(String token, UUID id, Map<String, Object> body) {
        return put(token, "/api/beneficiaries/" + id, body, Api.Beneficiary.class);
    }

    public Api.Beneficiary beneficiaryAction(String token, UUID id, String action) {
        return post(token, "/api/beneficiaries/" + id + "/" + action, null, Api.Beneficiary.class);
    }

    public List<Api.Account> accounts(String token) {
        return getList(token, "/api/accounts", new ParameterizedTypeReference<List<Api.Account>>() {
        });
    }

    // ------------------------------------------------------------------ company administration

    public List<Api.CompanyUser> companyUsers(String token) {
        return getList(token, "/api/company/users", new ParameterizedTypeReference<List<Api.CompanyUser>>() {
        });
    }

    public Api.CompanyUser inviteUser(String token, Map<String, Object> body) {
        return post(token, "/api/company/users/invites", body, Api.CompanyUser.class);
    }

    public Api.CompanyUser updateUser(String token, UUID id, Map<String, Object> body) {
        return put(token, "/api/company/users/" + id, body, Api.CompanyUser.class);
    }

    public Api.CompanyUser userStatus(String token, UUID id, String action) {
        return post(token, "/api/company/users/" + id + "/" + action, null, Api.CompanyUser.class);
    }

    public Api.Settings companySettings(String token) {
        return get(token, "/api/company/settings", Api.Settings.class);
    }

    public Api.WebhookConfig saveWebhook(String token, Map<String, Object> body) {
        return put(token, "/api/company/webhook", body, Api.WebhookConfig.class);
    }

    public Api.FormLayout saveFormLayout(String session, String layout) {
        return put(session, "/api/company/form-layout", Map.of("layout", layout), Api.FormLayout.class);
    }

    // ------------------------------------------------------------------ bulk files

    public List<Api.BulkFile> bulkFiles(String token) {
        return getList(token, "/api/bulk-files", new ParameterizedTypeReference<List<Api.BulkFile>>() {
        });
    }

    public Api.BulkFile bulkFile(String token, UUID id) {
        return get(token, "/api/bulk-files/" + id, Api.BulkFile.class);
    }

    public Api.BulkUploadResult uploadBulk(String token, Map<String, Object> body) {
        return post(token, "/api/bulk-files", body, Api.BulkUploadResult.class);
    }

    // ------------------------------------------------------------------ notifications

    public List<Api.Notification> notifications(String token, boolean unreadOnly) {
        return getList(token, "/api/notifications?unreadOnly=" + unreadOnly,
                new ParameterizedTypeReference<List<Api.Notification>>() {
                });
    }

    public long unreadCount(String token) {
        try {
            Map<String, Long> result = get(token, "/api/notifications/unread-count",
                    new ParameterizedTypeReference<Map<String, Long>>() {
                    });
            return result == null ? 0 : result.getOrDefault("unread", 0L);
        } catch (PaymentApiException e) {
            return 0;
        }
    }

    public void markRead(String token, UUID id) {
        post(token, "/api/notifications/" + id + "/read", null);
    }

    public void markAllRead(String token) {
        post(token, "/api/notifications/read-all", null);
    }

    // ------------------------------------------------------------------ bank operations

    public List<Api.Release> releases(String token, String status, String investigation) {
        StringBuilder uri = new StringBuilder("/api/bank/releases");
        boolean first = true;
        if (status != null && !status.isBlank()) {
            uri.append("?status=").append(status);
            first = false;
        }
        if (investigation != null && !investigation.isBlank()) {
            uri.append(first ? "?" : "&").append("investigation=").append(investigation);
        }
        return getList(token, uri.toString(), new ParameterizedTypeReference<List<Api.Release>>() {
        });
    }

    public void retryRelease(String token, UUID paymentId) {
        post(token, "/api/bank/releases/" + paymentId + "/retry", null);
    }

    public void investigate(String token, UUID paymentId, String status, String notes) {
        post(token, "/api/bank/releases/" + paymentId + "/investigation",
                Map.of("investigationStatus", status, "notes", notes == null ? "" : notes));
    }

    public void discardReleaseAttempt(String token, UUID paymentId) {
        sessionDelete("/api/bank/releases/" + paymentId + "/attempt", token);
    }

    public Api.Reversal requestReversal(String token, UUID paymentId, String reason) {
        return post(token, "/api/bank/payments/" + paymentId + "/reversals", Map.of("reason", reason),
                Api.Reversal.class);
    }

    public List<Api.Reversal> reversals(String token, String status) {
        String path = "/api/bank/reversals"
                + (status != null && !status.isBlank() ? "?status=" + status : "");
        return getList(token, path, new ParameterizedTypeReference<List<Api.Reversal>>() {
        });
    }

    public Api.PaymentDetail bankPayment(String token, UUID id) {
        return get(token, "/api/bank/payments/" + id, Api.PaymentDetail.class);
    }

    // ------------------------------------------------------------------ bank administration

    public List<Api.CompanyView> companies(String token) {
        return getList(token, "/api/bank/companies", new ParameterizedTypeReference<List<Api.CompanyView>>() {
        });
    }

    public Api.CompanyView company(String token, UUID id) {
        return get(token, "/api/bank/companies/" + id, Api.CompanyView.class);
    }

    public Api.CompanyView createCompany(String token, Map<String, Object> body) {
        return post(token, "/api/bank/companies", body, Api.CompanyView.class);
    }

    public Api.CompanyView setCompanyLimit(String token, UUID id, Map<String, Object> body) {
        return post(token, "/api/bank/companies/" + id + "/limit", body, Api.CompanyView.class);
    }

    public Api.CompanyUser inviteCompanyAdmin(String token, UUID companyId, Map<String, Object> body) {
        return post(token, "/api/bank/companies/" + companyId + "/admins/invite", body,
                Api.CompanyUser.class);
    }

    public Map<String, Object> verifyStaff(String session, String officerId) {
        return post(session, "/api/bank/staff/verify", Map.of("officerId", officerId),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    public List<Map<String, Object>> searchStaff(String session, String query) {
        return getList(session, "/api/bank/staff/search?query=" + query,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
    }

    public List<Api.Account> bankAccounts(String token, UUID companyId) {
        return getList(token, "/api/bank/accounts?companyId=" + companyId,
                new ParameterizedTypeReference<List<Api.Account>>() {
                });
    }

    public Api.Account createAccount(String token, Map<String, Object> body) {
        return post(token, "/api/bank/accounts", body, Api.Account.class);
    }

    public Api.Reversal decideReversal(String token, UUID id, boolean approve) {
        return post(token, "/api/bank/reversals/" + id + "/" + (approve ? "approve" : "decline"), null,
                Api.Reversal.class);
    }

    public Api.PageOfAudit auditPage(String token, Map<String, Object> params) {
        StringBuilder uri = new StringBuilder("/api/bank/audit?");
        params.forEach((k, v) -> {
            if (v != null && !String.valueOf(v).isBlank()) {
                uri.append(k).append('=').append(String.valueOf(v)).append('&');
            }
        });
        try {
            return sessionGet(uri.toString(), token).retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    // ------------------------------------------------------------------ export (CSV passthrough)

    public byte[] exportStatement(String token, Map<String, Object> params) {
        StringBuilder uri = new StringBuilder("/api/exports/statement?");
        params.forEach((k, v) -> {
            if (v != null && !String.valueOf(v).isBlank()) {
                uri.append(k).append('=').append(String.valueOf(v)).append('&');
            }
        });
        try {
            return sessionGet(uri.toString(), token).retrieve().body(byte[].class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    // ------------------------------------------------------------------ internal (service credential)

    public Api.StatusLookup lookupByRef(UUID companyId, String paymentRef) {
        try {
            return restClient.post().uri("/internal/payments/lookup")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalServiceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("companyId", companyId.toString(), "paymentRef", paymentRef))
                    .retrieve().body(Api.StatusLookup.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }

    public void activateUser(UUID userId) {
        try {
            restClient.post().uri("/internal/users/activate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalServiceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId.toString()))
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw translate(e);
        }
    }
}
