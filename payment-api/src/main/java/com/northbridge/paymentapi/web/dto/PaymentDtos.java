package com.northbridge.paymentapi.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response DTOs for payments, approvals, bulk files, releases and reversals. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentSummary(UUID id, String ref, String status, BigDecimal amount, String currency,
                                 BigDecimal fee, BigDecimal fxRate, BigDecimal settlementAmount,
                                 String settlementCurrency, LocalDate valueDate, String reference,
                                 String beneficiaryName, UUID beneficiaryId, String debitAccountNumber,
                                 UUID debitAccountId, UUID companyId, String createdByName, String reason,
                                 Instant createdAt, Instant updatedAt, boolean awaitingMyApproval,
                                 int approvalCount, String bulkFileRef) {
    }

    public record ApprovalActionView(String actorName, String decision, String comment,
                                     BigDecimal limitApplied, boolean halfRequired, Instant actedAt) {
    }

    public record AmendmentView(String actorName, List<ChangedFieldView> changedFields, Instant changedAt) {
        public record ChangedFieldView(String field, String from, String to) {
        }
    }

    public record CommentView(String authorName, String authorRole, String text, Instant createdAt) {
    }

    public record ReleaseView(UUID id, UUID paymentId, String paymentRef, String paymentStatus,
                              String coreRef,
                              BigDecimal postedAmount, String responseCode,
                              String reason, int retryCount, String investigationStatus,
                              String investigationNotes, Instant attemptedAt, Instant releasedAt) {
    }

    public record TimelineEntry(Instant eventAt, String actorType, String actorName, String action,
                                String summary, String authBasis) {
    }

    public record PaymentDetail(PaymentSummary summary, String beneficiaryAccountNumber,
                                String beneficiaryBankCode, String beneficiaryCurrency,
                                String amendedByName, List<AmendmentView> amendments,
                                List<ApprovalActionView> approvals, List<CommentView> comments,
                                ReleaseView release, List<TimelineEntry> timeline,
                                ApprovalContextView approvalContext) {
    }

    public record ApprovalContextView(boolean canApprove, boolean canReject, boolean canReturn,
                                      boolean canAmend, boolean canSubmit, BigDecimal myLimit,
                                      BigDecimal requiredAmount, boolean dualApproval,
                                      BigDecimal half, String companyDailyLimit, String utilisationUsed,
                                      String headroom, String baseCurrency) {
    }

    public record FeePreview(BigDecimal amount, String currency, BigDecimal fee, String rail,
                             BigDecimal flat, BigDecimal pct, BigDecimal min, BigDecimal max,
                             BigDecimal fxRate, BigDecimal settlementAmount, String settlementCurrency) {
    }

    public record BulkLine(UUID id, int lineNumber, String debitAccountNumber,
                           String beneficiaryAccountNumber, String bankCode, BigDecimal amount,
                           LocalDate valueDate, String reference, String status, String errorMessage,
                           UUID paymentId, String paymentRef) {
    }

    public record BulkFile(UUID id, String ref, String fileName, String status, BigDecimal declaredTotal,
                           int declaredCount, int parsedCount, int validCount, int invalidCount,
                           Instant uploadedAt, String uploadedByName, Instant processedAt,
                           List<BulkLine> lines) {
    }

    public record BulkUploadResult(UUID id, String ref, String fileName, String status) {
    }

    public record ReversalView(UUID id, UUID paymentId, String paymentRef, String reason, String status,
                               String requestedByName, Instant requestedAt, String approvedByName,
                               Instant decidedAt, String coreRef, Instant postedAt) {
    }
}
