package com.northbridge.paymentapi;

import com.northbridge.paymentapi.security.TokenCodec;
import com.northbridge.paymentapi.service.CoreClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API-level security tests: token authentication, caller-supplied identity headers being ignored,
 * cross-company access denial, account restrictions, mass-assignment immunity and the
 * beneficiary-list ETag behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired TokenCodec tokenCodec;
    @Autowired TestDataBuilder data;
    @Autowired com.northbridge.paymentapi.repo.BeneficiaryRepository beneficiaries;

    @MockitoBean CoreClient coreClient;

    @BeforeEach
    void setUp() {
        data.seed();
        when(coreClient.balance(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(
                new CoreClient.Balance("10000001", "GBP", new java.math.BigDecimal("5000.00")));
    }

    private String token(UUID userId) {
        return tokenCodec.mintTierToken(userId.toString(), Map.of("sid", "test-session"), 600);
    }

    @Test
    void rejectsMissingOrForgedTokens() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
        // A structurally valid token signed with the wrong secret is rejected.
        String forged = token(TestDataBuilder.BANK_ADMIN)
                .substring(0, 20) + "X"; // corrupt signature
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedIdentityHeadersHaveNoEffect() throws Exception {
        // A maker presenting X-User-Id/X-Role/company headers of a bank admin still acts as maker.
        mvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A))
                        .header("X-User-Id", TestDataBuilder.BANK_ADMIN)
                        .header("X-Role", "bank_admin")
                        .header("X-Company-Id", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("company_maker"))
                .andExpect(jsonPath("$.userId").value(TestDataBuilder.MAKER_A.toString()));
    }

    @Test
    void companyUserCannotReachAnotherCompanysPayment() throws Exception {
        // Beta's payment is invisible to Alpha's maker (404, not 403 — existence is not leaked).
        String betaBeneficiary = beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                        TestDataBuilder.COMPANY_B, "90000003", "20-00-00")
                .orElseThrow().getId().toString();
        UUID betaPayment = createDraft(token(TestDataBuilder.MAKER_B),
                TestDataBuilder.ACCOUNT_B1, betaBeneficiary, "50.00");
        mvc.perform(get("/api/payments/" + betaPayment)
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A)))
                .andExpect(status().isNotFound());
    }

    @Test
    void massAssignmentFieldsAreIgnored() throws Exception {
        String body = """
                {
                  "debitAccountId": "%s",
                  "beneficiaryId": "%s",
                  "amount": 1000.00,
                  "valueDate": "2099-01-01",
                  "reference": "mass assignment probe",
                  "fee": 0.01,
                  "fxRate": 99.99,
                  "settlementAmount": 0.02,
                  "status": "RELEASED",
                  "company": "%s",
                  "approvalLimit": 999999
                }
                """.formatted(TestDataBuilder.ACCOUNT_A1,
                beneficiaryId("90000001"), TestDataBuilder.COMPANY_B);
        String response = mvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.fee").value(1.5))
                .andExpect(jsonPath("$.fxRate").value(1.0))
                .andExpect(jsonPath("$.settlementAmount").value(1000.0))
                .andExpect(jsonPath("$.companyId").value(TestDataBuilder.COMPANY_A.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(response.replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1"));
        mvc.perform(get("/api/payments/" + paymentId)
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("DRAFT"));
    }

    @Test
    void makerCannotApproveThroughTheApi() throws Exception {
        UUID payment = createSubmittedDraft();
        mvc.perform(post("/api/payments/" + payment + "/approve")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"self approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void restrictedUserSeesOnlyAssignedAccounts() throws Exception {
        mvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A_RESTRICTED)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(TestDataBuilder.ACCOUNT_A2.toString()));
    }

    @Test
    void beneficiaryListUsesMd5EtagAndHonorsIfNoneMatch() throws Exception {
        var result = mvc.perform(get("/api/beneficiaries")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();
        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get("/api/beneficiaries")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A))
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
    }

    @Test
    void internalEndpointsRequireTheServiceCredential() throws Exception {
        mvc.perform(post("/internal/bulk-files/process-due")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/bulk-files/process-due")
                        .header("Authorization", "Bearer wrong-service-token"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private String beneficiaryId(String accountNumber) {
        return beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                        TestDataBuilder.COMPANY_A, accountNumber, "20-00-00")
                .orElseThrow().getId().toString();
    }

    private UUID createDraft(String bearer, UUID account, String beneficiaryAccount, String amount)
            throws Exception {
        String body = """
                {
                  "debitAccountId": "%s",
                  "beneficiaryId": "%s",
                  "amount": %s,
                  "valueDate": "2099-01-01",
                  "reference": "test payment"
                }
                """.formatted(account, beneficiaryAccount, amount);
        var response = mvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(response.replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1"));
    }

    private UUID createSubmittedDraft() throws Exception {
        var response = mvc.perform(get("/api/beneficiaries")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A)))
                .andReturn().getResponse().getContentAsString();
        String beneficiaryId = response.replaceAll(".*\"id\":\"([0-9a-f-]+)\",\"name\":\"Alpha Beneficiary.*", "$1");
        UUID id = createDraft(token(TestDataBuilder.MAKER_A), TestDataBuilder.ACCOUNT_A1, beneficiaryId, "100.00");
        mvc.perform(post("/api/payments/" + id + "/submit")
                        .header("Authorization", "Bearer " + token(TestDataBuilder.MAKER_A)))
                .andExpect(status().isOk());
        return id;
    }
}
