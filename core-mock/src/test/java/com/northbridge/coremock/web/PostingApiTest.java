package com.northbridge.coremock.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northbridge.coremock.support.TestScopeTokenFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP tests of the posting endpoint's security model: API key
 * enforcement and scope enforcement, plus the happy path. The test profile
 * seeds the demo accounts (20301001 belongs to company C1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostingApiTest {

    private static final String API_KEY = "test-core-api-key-0123456789";
    private static final String SCOPE_SECRET = "test-scope-secret-0123456789abcdef";
    private static final UUID C1 = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID C2 = UUID.fromString("00000000-0000-4000-8000-0000000000c2");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String companyToken(UUID companyId, List<String> accounts) {
        Instant now = Instant.now();
        return TestScopeTokenFactory.mintCompany(SCOPE_SECRET, "payment-api", companyId, accounts,
                now, now.plusSeconds(300));
    }

    private String postingBody(String requestRef, String accountNumber) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "requestRef", requestRef,
                "accountNumber", accountNumber,
                "postingType", "DEBIT",
                "amount", "10.00",
                "currency", "GBP",
                "counterparty", "TEST-API"));
    }

    @Test
    void missingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/v1/postings")
                        .header("X-Scope-Token", companyToken(C1, List.of("20301001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postingBody("API-TEST-NOKEY", "20301001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("CORE_AUTH_FAILED"));
    }

    @Test
    void wrongApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/v1/postings")
                        .header("X-Core-Api-Key", "wrong-api-key-value-1234")
                        .header("X-Scope-Token", companyToken(C1, List.of("20301001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postingBody("API-TEST-WRONGKEY", "20301001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("CORE_AUTH_FAILED"));
    }

    @Test
    void missingScopeTokenReturns401() throws Exception {
        mockMvc.perform(post("/v1/postings")
                        .header("X-Core-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postingBody("API-TEST-NOTOKEN", "20301001")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("CORE_AUTH_FAILED"));
    }

    @Test
    void wrongCompanyScopeReturns403() throws Exception {
        mockMvc.perform(post("/v1/postings")
                        .header("X-Core-Api-Key", API_KEY)
                        .header("X-Scope-Token", companyToken(C2, List.of("20442011")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postingBody("API-TEST-WRONGCOMPANY", "20301001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CORE_SCOPE_DENIED"));
    }

    @Test
    void accountOutsideTokenAccountsReturns403() throws Exception {
        // Scope covers 20301002 only; the body targets 20301001.
        mockMvc.perform(post("/v1/postings")
                        .header("X-Core-Api-Key", API_KEY)
                        .header("X-Scope-Token", companyToken(C1, List.of("20301002")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postingBody("API-TEST-OUTSIDE", "20301001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CORE_SCOPE_DENIED"));
    }

    @Test
    void validKeyAndScopePostSuccessfully() throws Exception {
        mockMvc.perform(post("/v1/postings")
                        .header("X-Core-Api-Key", API_KEY)
                        .header("X-Scope-Token", companyToken(C1, List.of("20301001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postingBody("API-TEST-OK", "20301001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.coreRef").isNotEmpty())
                .andExpect(jsonPath("$.balance").value(84510.00));
    }

    @Test
    void balanceEndpointEnforcesScope() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/accounts/20301001/balance")
                        .header("X-Core-Api-Key", API_KEY)
                        .header("X-Scope-Token", companyToken(C2, List.of("20442011"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CORE_SCOPE_DENIED"));
    }
}
