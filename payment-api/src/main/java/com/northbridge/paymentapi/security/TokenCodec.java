package com.northbridge.paymentapi.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HS256 token codec shared by the three tiers.
 *
 * <p>The channel tier (portal) mints a short-lived opaque session token for every call it makes to
 * this decision tier. This tier verifies the signature and then resolves the acting user from ITS
 * OWN DATABASE — the token payload is never treated as an authorization claim on its own.</p>
 *
 * <p>This tier also mints the short-lived scope tokens presented to the vendor core stub.</p>
 */
@Component
public class TokenCodec {

    public static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final String tierSecret;
    private final String coreScopeSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenCodec(@Value("${app.security.tier-token-secret}") String tierSecret,
                      @Value("${app.security.core-scope-secret}") String coreScopeSecret) {
        this.tierSecret = requireSecret(tierSecret, "TIER_TOKEN_SECRET");
        this.coreScopeSecret = requireSecret(coreScopeSecret, "CORE_SCOPE_SECRET");
    }

    private static String requireSecret(String value, String name) {
        if (value == null || value.isBlank() || value.length() < 16) {
            throw new IllegalStateException(
                    "Missing or too-short " + name + ". This tier refuses to start without its signing secret. "
                            + "Set it via environment (see .env.example / README).");
        }
        return value;
    }

    /** Mints a tier-to-tier token (session token or service token). */
    public String mintTierToken(String subject, Map<String, Object> extraClaims, long ttlSeconds) {
        Map<String, Object> claims = new LinkedHashMap<>(extraClaims);
        claims.put("sub", subject);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
        return sign(tierSecret, claims);
    }

    /** Verifies a tier-to-tier token; returns the claims map or null when invalid/expired. */
    public Map<String, Object> verifyTierToken(String token) {
        return verify(tierSecret, token);
    }

    /** Mints the short-lived scope token presented to core-mock. */
    public String mintCoreScopeToken(Map<String, Object> claims, long ttlSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>(claims);
        payload.put("sub", "payment-api");
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
        return sign(coreScopeSecret, payload);
    }

    private String sign(String secret, Map<String, Object> claims) {
        try {
            String headerB64 = b64(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = b64(objectMapper.writeValueAsBytes(claims));
            String signingInput = headerB64 + "." + payloadB64;
            byte[] sig = hmac(secret, signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + b64(sig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint token", e);
        }
    }

    private Map<String, Object> verify(String secret, String token) {
        try {
            if (token == null || token.isBlank()) {
                return null;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            if (!header.contains("\"alg\":\"HS256\"")) {
                return null;
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] expected = hmac(secret, signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] provided = Base64.getUrlDecoder().decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, provided)) {
                return null;
            }
            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() { });
            Object exp = claims.get("exp");
            if (!(exp instanceof Number expNum) || expNum.longValue() + 5 < Instant.now().getEpochSecond()) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hmac(String secret, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
