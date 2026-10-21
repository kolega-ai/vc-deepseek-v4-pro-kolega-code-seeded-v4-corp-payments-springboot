package com.northbridge.paymentapi;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Service-level tests for the authorization and workflow rules that the acceptance criteria
 * depend on: maker/checker separation, dual approval, limit enforcement, cooling periods,
 * daily headroom and the concurrent-release guarantee.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApprovalRulesTest {

    @Autowired TestDataBuilder data;
    @Autowired PaymentWorkflowService workflow;
    @Autowired ApprovalService approvalService;
    @Autowired PaymentQueryService queryService;
    @Autowired BeneficiaryService beneficiaryService;
    @Autowired BulkFileService bulkFileService;
    @Autowired ReleaseService releaseService;
    @Autowired ReversalService reversalService;
    @Autowired LimitService limitService;
    @Autowired PaymentRepository payments;
    @Autowired BeneficiaryRepository beneficiaries;
    @Autowired BulkFileRepository bulkFiles;
    @Autowired BulkFileLineRepository bulkLines;
    @Autowired ReleaseRepository releases;
    @Autowired AuditEventRepository auditEvents;
    @Autowired ReversalRepository reversals;
    @Autowired CompanyRepository companies;

    @MockitoBean CoreClient coreClient;

    @BeforeEach
    void setUp() {
        // FK-safe order: dependent rows first (lines reference payments; payments reference files).
        reversals.deleteAll();
        releases.deleteAll();
        bulkLines.deleteAll();
        payments.deleteAll();    // cascades approvals/amendments
        bulkFiles.deleteAll();
        beneficiaries.deleteAll();
        auditEvents.deleteAll();
        // Reference tables and companies/users persist; re-seed beneficiaries (deleted above).
        data.seed();
    }

    private UUID draft(String amount, UUID account, String beneficiaryAccount) {
        var beneficiary = beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                TestDataBuilder.COMPANY_A, beneficiaryAccount, "20-00-00").orElseThrow();
        return workflow.createDraft(data.principal(TestDataBuilder.MAKER_A), account, beneficiary.getId(),
                new BigDecimal(amount), LocalDate.now().plusDays(1), "Test payment");
    }

    private UUID submitted(String amount, UUID account, String beneficiaryAccount) {
        UUID id = draft(amount, account, beneficiaryAccount);
        workflow.submit(data.principal(TestDataBuilder.MAKER_A), id);
        return id;
    }

    @Test
    void makerCannotApproveAndCheckerCannotApproveOwnPayment() {
        UUID id = submitted("100.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        var ex1 = assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.MAKER_A), id, null));
        assertEquals(ApiException.Code.FORBIDDEN, ex1.getCode());
        // The checker who created it cannot approve it either.
        UUID own = draft("200.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        // makers cannot approve at all
        assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.MAKER_A), own, null));
    }

    @Test
    void singleSufficientCheckerApprovesAlone() {
        UUID id = submitted("1000.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), id, "ok");
        assertEquals(PaymentStatus.APPROVED, payments.findById(id).orElseThrow().getStatus());
    }

    @Test
    void dualApprovalPathRequiresTwoDifferentCheckersWithHalfLimit() {
        // 30,000 > every single checker limit (25k), half = 15,000.
        UUID id = submitted("30000.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        // Small checker (limit 10,000 < half 15,000) cannot even start the dual path.
        var ex = assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_SMALL), id, null));
        assertEquals(ApiException.Code.APPROVAL_LIMIT_EXCEEDED, ex.getCode());

        // Big checker (25k >= half) → partially_approved.
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), id, "first");
        assertEquals(PaymentStatus.PARTIALLY_APPROVED, payments.findById(id).orElseThrow().getStatus());

        // The same checker cannot record the second approval.
        var ex2 = assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), id, "again"));
        assertEquals(ApiException.Code.SELF_ACTION_FORBIDDEN, ex2.getCode());

        // Small checker cannot complete it either (limit < half).
        var ex3 = assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_SMALL), id, null));
        assertEquals(ApiException.Code.APPROVAL_LIMIT_EXCEEDED, ex3.getCode());
    }

    @Test
    void companyAdminAndBankUsersCannotApproveCustomerPayments() {
        UUID id = submitted("500.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.ADMIN_A), id, null));
        assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.OPS), id, null));
        assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.BANK_ADMIN), id, null));
        assertEquals(PaymentStatus.PENDING_APPROVAL, payments.findById(id).orElseThrow().getStatus());
    }

    @Test
    void checkerCannotApproveBeyondServerSideLimitEvenWithSubmittedLimitField() {
        // The approval request DTO has no limit field; the limit is read server-side only.
        // CHECKER_A_SMALL has limit 10,000; a 12,000 payment is above it.
        UUID big = submitted("12000.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        var ex = assertThrows(ApiException.class,
                () -> approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_SMALL), big, null));
        assertEquals(ApiException.Code.APPROVAL_LIMIT_EXCEEDED, ex.getCode());
        assertEquals(PaymentStatus.PENDING_APPROVAL, payments.findById(big).orElseThrow().getStatus());
    }

    @Test
    void accountRestrictionBlocksUnassignedAccount() {
        // Unrestricted maker may use any account; the restricted maker may only use ACCOUNT_A2.
        UUID unrestricted = draft("100.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        assertNotNull(unrestricted);
        UUID id = workflow.createDraft(data.principal(TestDataBuilder.MAKER_A_RESTRICTED),
                TestDataBuilder.ACCOUNT_A2,
                beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                        TestDataBuilder.COMPANY_A, "90000001", "20-00-00").orElseThrow().getId(),
                new BigDecimal("100.00"), LocalDate.now().plusDays(1), "ok");
        assertNotNull(id);
        var ex2 = assertThrows(ApiException.class,
                () -> workflow.createDraft(data.principal(TestDataBuilder.MAKER_A_RESTRICTED),
                        TestDataBuilder.ACCOUNT_A1,
                        beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                                TestDataBuilder.COMPANY_A, "90000001", "20-00-00").orElseThrow().getId(),
                        new BigDecimal("100.00"), LocalDate.now().plusDays(1), "ok"));
        assertEquals(ApiException.Code.ACCOUNT_NOT_ASSIGNED, ex2.getCode());
    }

    @Test
    void coolingBeneficiaryCannotReceivePaymentAndAmendmentRestartsCooling() {
        var cooling = beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                TestDataBuilder.COMPANY_A, "90000002", "20-00-00").orElseThrow();
        var ex = assertThrows(ApiException.class,
                () -> workflow.createDraft(data.principal(TestDataBuilder.MAKER_A),
                        TestDataBuilder.ACCOUNT_A1, cooling.getId(), new BigDecimal("10.00"),
                        LocalDate.now().plusDays(1), "cooling"));
        assertEquals(ApiException.Code.BENEFICIARY_NOT_PAYABLE, ex.getCode());

        // Amending the account number restarts the cooling period on an active beneficiary.
        var active = beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                TestDataBuilder.COMPANY_A, "90000001", "20-00-00").orElseThrow();
        beneficiaryService.amend(data.principal(TestDataBuilder.ADMIN_A), active.getId(), null,
                "90000009", null, null);
        var amended = beneficiaries.findById(active.getId()).orElseThrow();
        assertEquals(BeneficiaryStatus.PENDING_COOLING, amended.getStatus());
        assertTrue(amended.getCoolingUntil().isAfter(Instant.now()));
    }

    @Test
    void dailyHeadroomDecreasesOnApprovalAndIsRestoredWhenReversalPosts() {
        var company = companies.findById(TestDataBuilder.COMPANY_A).orElseThrow();
        var before = limitService.utilisationToday(TestDataBuilder.COMPANY_A, company);
        // Approve a same-day payment of 1,000 → contribution 1,000 + fee 1.50 = 1,001.50.
        UUID id = workflow.createDraft(data.principal(TestDataBuilder.MAKER_A),
                TestDataBuilder.ACCOUNT_A1,
                beneficiaries.findByCompanyIdAndAccountNumberAndBankCode(
                        TestDataBuilder.COMPANY_A, "90000001", "20-00-00").orElseThrow().getId(),
                new BigDecimal("1000.00"), LocalDate.now(), "headroom");
        workflow.submit(data.principal(TestDataBuilder.MAKER_A), id);
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), id, "ok");
        var afterApproval = limitService.utilisationToday(TestDataBuilder.COMPANY_A, company);
        assertEquals(new BigDecimal("1001.50"),
                afterApproval.used().subtract(before.used()).setScale(2));

        // Release + reversal posted → contribution restored.
        when(coreClient.post(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenReturn(new CoreClient.PostingResult("CORE-TEST-1", "POSTED", "00", null, false));
        releaseService.releasePayment(id, true);
        assertEquals(PaymentStatus.RELEASED, payments.findById(id).orElseThrow().getStatus());
        var afterRelease = limitService.utilisationToday(TestDataBuilder.COMPANY_A, company);
        assertEquals(afterApproval.used(), afterRelease.used());

        var reversal = reversalService.request(data.principal(TestDataBuilder.OPS), id, "test reversal");
        var decided = reversalService.decide(data.principal(TestDataBuilder.BANK_ADMIN), reversal.getId(), true);
        assertTrue(reversalService.post(decided));
        var afterReversal = limitService.utilisationToday(TestDataBuilder.COMPANY_A, company);
        assertEquals(0, before.used().compareTo(afterReversal.used()));
        assertEquals(PaymentStatus.REVERSED, payments.findById(id).orElseThrow().getStatus());
    }

    @Test
    void bulkFileTotalsMismatchRejectsWholeFileAndInvalidLinesDoNotBlockValidOnes() {
        var maker = data.principal(TestDataBuilder.MAKER_A);
        // Mismatch: declared 500 but lines sum 450 → whole file rejected.
        var bad = bulkFileService.upload(maker, "bad.csv", new BigDecimal("500.00"), 2, java.util.List.of(
                new BulkFileService.RawLine(1, "10000001,90000001,20-00-00,250.00," + LocalDate.now() + ",line1"),
                new BulkFileService.RawLine(2, "10000001,90000001,20-00-00,200.00," + LocalDate.now() + ",line2")));
        bulkFileService.process(bad);
        assertEquals(BulkFileStatus.REJECTED, bulkFiles.findById(bad.getId()).orElseThrow().getStatus());

        // One invalid line (unknown beneficiary) + one valid line → parsed_with_errors,
        // valid line becomes a pending payment.
        var mixed = bulkFileService.upload(maker, "mixed.csv", new BigDecimal("350.00"), 2, java.util.List.of(
                new BulkFileService.RawLine(1, "10000001,90000001,20-00-00,250.00," + LocalDate.now() + ",valid"),
                new BulkFileService.RawLine(2, "10000001,99999999,20-00-00,100.00," + LocalDate.now() + ",invalid")));
        bulkFileService.process(mixed);
        var file = bulkFiles.findById(mixed.getId()).orElseThrow();
        assertEquals(BulkFileStatus.PARSED_WITH_ERRORS, file.getStatus());
        assertEquals(1, file.getValidCount());
        assertEquals(1, file.getInvalidCount());
        var validLine = bulkLines.findByBulkFileIdOrderByLineNumberAsc(file.getId()).stream()
                .filter(l -> l.getStatus() == BulkLineStatus.VALID).findFirst().orElseThrow();
        var validPayment = payments.findById(validLine.getPayment().getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING_APPROVAL, validPayment.getStatus());
    }

    @Test
    void concurrentReleasesOverBalanceProduceExactlyOnePosting() {
        // Two approved payments, same account, 80 + 60 = 140 against a mocked balance of 100.
        UUID p1 = submitted("80.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        UUID p2 = submitted("60.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), p1, null);
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), p2, null);

        final java.math.BigDecimal[] balance = {new java.math.BigDecimal("100.00")};
        AtomicInteger postings = new AtomicInteger();
        when(coreClient.post(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    java.math.BigDecimal amount = inv.getArgument(3);
                    synchronized (balance) {
                        if (amount.compareTo(balance[0]) > 0) {
                            return new CoreClient.PostingResult(null, "INSUFFICIENT_FUNDS", "51", null, false);
                        }
                        balance[0] = balance[0].subtract(amount);
                        postings.incrementAndGet();
                        return new CoreClient.PostingResult("CORE-T" + postings.get(), "POSTED", "00", null, false);
                    }
                });

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Thread t1 = new Thread(() -> { try { start.await(); releaseService.releasePayment(p1, true); } catch (Exception ignored) { } finally { done.countDown(); } }, "t1");
        Thread t2 = new Thread(() -> { try { start.await(); releaseService.releasePayment(p2, true); } catch (Exception ignored) { } finally { done.countDown(); } }, "t2");
        t1.start();
        t2.start();
        start.countDown();
        try {
            done.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        assertEquals(1, postings.get(), "exactly one posting must reach core");
        long releasedCount = java.util.List.of(p1, p2).stream()
                .map(id -> payments.findById(id).orElseThrow().getStatus())
                .filter(s -> s == PaymentStatus.RELEASED).count();
        long failedCount = java.util.List.of(p1, p2).stream()
                .map(id -> payments.findById(id).orElseThrow().getStatus())
                .filter(s -> s == PaymentStatus.FAILED).count();
        assertEquals(1, releasedCount);
        assertEquals(1, failedCount);
        var failedPayment = payments.findById(
                payments.findById(p1).orElseThrow().getStatus() == PaymentStatus.FAILED ? p1 : p2).orElseThrow();
        assertEquals("insufficient_funds", failedPayment.getReason());
    }

    @Test
    void duplicateReleaseIsRejectedAndNeverPostsTwice() {
        UUID id = submitted("500.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), id, null);
        AtomicInteger postings = new AtomicInteger();
        when(coreClient.post(anyString(), anyString(), anyString(), any(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    postings.incrementAndGet();
                    return new CoreClient.PostingResult("CORE-DUP-1", "POSTED", "00", null, false);
                });
        releaseService.releasePayment(id, true);
        var ex = assertThrows(ApiException.class, () -> releaseService.releasePayment(id, true));
        assertEquals(ApiException.Code.DUPLICATE_RELEASE, ex.getCode());
        assertEquals(1, postings.get());
    }

    @Test
    void everyTransitionWritesAnAuditEvent() {
        UUID id = submitted("250.00", TestDataBuilder.ACCOUNT_A1, "90000001");
        approvalService.approve(data.principal(TestDataBuilder.CHECKER_A_BIG), id, "audited");
        var events = auditEvents.findTop20ByResourceIdOrderByEventAtDesc(id);
        assertTrue(events.stream().anyMatch(e -> "PAYMENT_SUBMITTED".equals(e.getAction())));
        assertTrue(events.stream().anyMatch(e -> "PAYMENT_APPROVED".equals(e.getAction())));
        assertTrue(events.stream().allMatch(e -> e.getAuthBasis() != null && !e.getAuthBasis().isBlank()));
        assertTrue(events.stream().anyMatch(e -> "Alpha Checker".equals(e.getActorName())));
    }
}
