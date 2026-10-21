package com.northbridge.coremock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.northbridge.coremock.config.AppEnvProperties;
import com.northbridge.coremock.support.TestScopeTokenFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeTokenVerifierTest {

    private static final String SECRET = "test-scope-secret-0123456789abcdef";
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");

    private ScopeTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        AppEnvProperties properties = new AppEnvProperties();
        properties.setCoreApiKey("unused-test-api-key");
        properties.setCoreScopeSecret(SECRET);
        verifier = new ScopeTokenVerifier(properties, new ObjectMapper());
    }

    @Test
    void verifiesValidCompanyTokenRoundTrip() {
        Instant now = Instant.now();
        String token = TestScopeTokenFactory.mintCompany(SECRET, "payment-api", COMPANY_ID,
                List.of("20301001", "20301002"), now, now.plusSeconds(300));

        ScopeToken parsed = verifier.verify(token);

        assertThat(parsed).isNotNull();
        assertThat(parsed.subject()).isEqualTo("payment-api");
        assertThat(parsed.scope().isCompany()).isTrue();
        assertThat(parsed.scope().companyId()).isEqualTo(COMPANY_ID);
        assertThat(parsed.scope().accounts()).containsExactly("20301001", "20301002");
        assertThat(parsed.expiresAt()).isEqualTo(now.plusSeconds(300).getEpochSecond());
    }

    @Test
    void verifiesValidBankTokenRoundTrip() {
        Instant now = Instant.now();
        String token = TestScopeTokenFactory.mintBank(SECRET, "payment-api", now, now.plusSeconds(300));

        ScopeToken parsed = verifier.verify(token);

        assertThat(parsed).isNotNull();
        assertThat(parsed.isBankScope()).isTrue();
        assertThat(parsed.scope().operation()).isEqualTo("postings");
    }

    @Test
    void rejectsTamperedSignature() {
        Instant now = Instant.now();
        String token = TestScopeTokenFactory.mintCompany(SECRET, "payment-api", COMPANY_ID,
                List.of("20301001"), now, now.plusSeconds(300));
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(verifier.verify(tampered)).isNull();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        Instant now = Instant.now();
        String token = TestScopeTokenFactory.mintCompany("another-secret-0123456789abcdef", "payment-api", COMPANY_ID,
                List.of("20301001"), now, now.plusSeconds(300));

        assertThat(verifier.verify(token)).isNull();
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        String token = TestScopeTokenFactory.mintCompany(SECRET, "payment-api", COMPANY_ID,
                List.of("20301001"), now.minusSeconds(600), now.minusSeconds(10));

        assertThat(verifier.verify(token)).isNull();
    }

    @Test
    void acceptsTokenWithinClockSkew() {
        Instant now = Instant.now();
        // exp is 3 seconds in the past — inside the allowed 5s skew window.
        String token = TestScopeTokenFactory.mintCompany(SECRET, "payment-api", COMPANY_ID,
                List.of("20301001"), now.minusSeconds(300), now.minusSeconds(3));

        assertThat(verifier.verify(token)).isNotNull();
    }

    @Test
    void rejectsTokenWithoutScope() {
        Instant now = Instant.now();
        ObjectNode payload = TestScopeTokenFactory.basePayload("payment-api", now, now.plusSeconds(300));
        String token = TestScopeTokenFactory.mint(SECRET, payload);

        assertThat(verifier.verify(token)).isNull();
    }

    @Test
    void rejectsTokenWithUnknownScopeType() {
        Instant now = Instant.now();
        ObjectNode payload = TestScopeTokenFactory.basePayload("payment-api", now, now.plusSeconds(300));
        ObjectNode scope = payload.putObject("scope");
        scope.put("type", "root");
        String token = TestScopeTokenFactory.mint(SECRET, payload);

        assertThat(verifier.verify(token)).isNull();
    }

    @Test
    void rejectsCompanyScopeWithInvalidCompanyId() {
        Instant now = Instant.now();
        ObjectNode payload = TestScopeTokenFactory.basePayload("payment-api", now, now.plusSeconds(300));
        ObjectNode scope = payload.putObject("scope");
        scope.put("type", "company");
        scope.put("companyId", "not-a-uuid");
        scope.putArray("accounts").add("20301001");
        String token = TestScopeTokenFactory.mint(SECRET, payload);

        assertThat(verifier.verify(token)).isNull();
    }

    @Test
    void rejectsMalformedTokens() {
        Instant now = Instant.now();
        assertThat(verifier.verify(null)).isNull();
        assertThat(verifier.verify("")).isNull();
        assertThat(verifier.verify("garbage")).isNull();
        assertThat(verifier.verify("only.two.parts.missing.one")).isNull();
        assertThat(verifier.verify("a.b.c")).isNull(); // segments are not valid base64url
        assertThat(verifier.verify("!!!.###.$$$")).isNull();
    }

    @Test
    void rejectsNonHs256Algorithm() throws Exception {
        Instant now = Instant.now();
        ObjectNode payload = TestScopeTokenFactory.basePayload("payment-api", now, now.plusSeconds(300));
        payload.putObject("scope").put("type", "bank").put("op", "postings");

        // Re-sign with an HS384 header — the verifier only accepts HS256.
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode header = mapper.createObjectNode();
        header.put("alg", "HS384");
        header.put("typ", "JWT");
        String headerPart = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(header));
        String payloadPart = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(payload));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((headerPart + "." + payloadPart).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = headerPart + "." + payloadPart + "."
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        assertThat(verifier.verify(token)).isNull();
    }
}
