package com.northbridge.coremock.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northbridge.coremock.config.AppEnvProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Verifies the scope tokens minted by the payment-api decision tier.
 *
 * <p>Token format is JWT-like: {@code base64url(header).base64url(payload).base64url(sig)}
 * where the header is {@code {"alg":"HS256","typ":"JWT"}} and the signature is
 * an HMAC-SHA256 over the first two segments keyed with the UTF-8 bytes of the
 * shared {@code CORE_SCOPE_SECRET}.
 *
 * <p>Verification is ALL this class does — core-mock never mints tokens. A
 * token is accepted only when its signature is valid, {@code exp} has not
 * passed (allowing 5s of clock skew) and a well-formed scope object is present.
 * Any failure returns {@code null}; there is deliberately no path that returns
 * a partially trusted token.
 */
@Component
public class ScopeTokenVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String EXPECTED_JWT_ALGORITHM = "HS256";
    private static final long CLOCK_SKEW_SECONDS = 5;

    private final byte[] secretBytes;
    private final ObjectMapper objectMapper;

    public ScopeTokenVerifier(AppEnvProperties properties, ObjectMapper objectMapper) {
        this.secretBytes = properties.getCoreScopeSecret().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    /**
     * Verifies a raw {@code X-Scope-Token} header value.
     *
     * @param token the raw token string, may be {@code null}
     * @return the parsed {@link ScopeToken}, or {@code null} if the token is
     * missing, malformed, signed with a different key, expired, or lacks a
     * well-formed scope
     */
    public ScopeToken verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (header == null || !EXPECTED_JWT_ALGORITHM.equals(header.path("alg").asText(null))) {
                return null;
            }

            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expectedSignature = hmac(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(providedSignature, expectedSignature)) {
                return null;
            }

            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (payload == null) {
                return null;
            }
            JsonNode sub = payload.get("sub");
            JsonNode iat = payload.get("iat");
            JsonNode exp = payload.get("exp");
            if (sub == null || !sub.isTextual() || sub.asText().isBlank()
                    || iat == null || !iat.isIntegralNumber()
                    || exp == null || !exp.isIntegralNumber()) {
                return null;
            }

            long expiresAt = exp.asLong();
            long now = Instant.now().getEpochSecond();
            if (expiresAt <= now - CLOCK_SKEW_SECONDS) {
                return null;
            }

            ScopeToken.Scope scope = parseScope(payload.get("scope"));
            if (scope == null) {
                return null;
            }
            return new ScopeToken(sub.asText(), iat.asLong(), expiresAt, scope);
        } catch (Exception e) {
            return null;
        }
    }

    private ScopeToken.Scope parseScope(JsonNode scopeNode) {
        if (scopeNode == null || !scopeNode.isObject()) {
            return null;
        }
        String type = scopeNode.path("type").asText(null);
        if ("company".equals(type)) {
            String companyIdRaw = scopeNode.path("companyId").asText(null);
            UUID companyId;
            try {
                companyId = companyIdRaw == null ? null : UUID.fromString(companyIdRaw);
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (companyId == null) {
                return null;
            }
            JsonNode accountsNode = scopeNode.get("accounts");
            if (accountsNode == null || !accountsNode.isArray() || accountsNode.isEmpty()) {
                return null;
            }
            List<String> accounts = new ArrayList<>(accountsNode.size());
            for (JsonNode accountNode : accountsNode) {
                if (accountNode == null || !accountNode.isTextual() || accountNode.asText().isBlank()) {
                    return null;
                }
                accounts.add(accountNode.asText());
            }
            return ScopeToken.Scope.company(companyId, accounts);
        }
        if ("bank".equals(type)) {
            if (!"postings".equals(scopeNode.path("op").asText(null))) {
                return null;
            }
            return ScopeToken.Scope.bank();
        }
        return null;
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable in this JVM", e);
        }
    }
}
