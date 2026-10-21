package com.northbridge.coremock.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Test-side duplicate of the token minting performed by the payment-api tier.
 *
 * <p>Production core-mock code only VERIFIES scope tokens; it never mints
 * them. Tests mint real HMAC-SHA256-signed tokens with this factory so the
 * verifier and the HTTP security layer are exercised against genuine payloads
 * rather than hand-built fakes.
 */
public final class TestScopeTokenFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestScopeTokenFactory() {
    }

    /** Mints a company-scope token for the given company and account list. */
    public static String mintCompany(String secret, String subject, UUID companyId,
                                     List<String> accounts, Instant issuedAt, Instant expiresAt) {
        ObjectNode payload = basePayload(subject, issuedAt, expiresAt);
        ObjectNode scope = payload.putObject("scope");
        scope.put("type", "company");
        scope.put("companyId", companyId.toString());
        ArrayNode accountsNode = scope.putArray("accounts");
        accounts.forEach(accountsNode::add);
        return sign(secret, payload);
    }

    /** Mints a bank-scope token ({@code {"type":"bank","op":"postings"}}). */
    public static String mintBank(String secret, String subject, Instant issuedAt, Instant expiresAt) {
        ObjectNode payload = basePayload(subject, issuedAt, expiresAt);
        ObjectNode scope = payload.putObject("scope");
        scope.put("type", "bank");
        scope.put("op", "postings");
        return sign(secret, payload);
    }

    /** Mints a token from a caller-supplied payload — used for negative tests. */
    public static String mint(String secret, ObjectNode payload) {
        return sign(secret, payload);
    }

    public static ObjectNode basePayload(String subject, Instant issuedAt, Instant expiresAt) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("sub", subject);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        return payload;
    }

    private static String sign(String secret, ObjectNode payload) {
        try {
            ObjectNode header = MAPPER.createObjectNode();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerPart = base64Url(MAPPER.writeValueAsBytes(header));
            String payloadPart = base64Url(MAPPER.writeValueAsBytes(payload));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal((headerPart + "." + payloadPart).getBytes(StandardCharsets.UTF_8));
            return headerPart + "." + payloadPart + "." + base64Url(signature);
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new IllegalStateException("Failed to mint test scope token", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
