package com.northbridge.portal.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Mirrors of the payment-api response DTOs (JSON contract between the two tiers). */
public final class Api {

    private Api() {
    }

    public record UserProfile(UUID userId, String email, String fullName, String role,
                              UUID companyId, String companyName, String companyCurrency,
                              String companyStatus, BigDecimal approvalLimit, List<UUID> accountIds,
                              String userStatus, boolean canCreatePayments, boolean canApprove,
                              boolean canManageCompany, boolean isBankOps, boolean isBankAdmin,
                              BigDecimal companyDailyLimit) {
    }

    public record PaymentSummary(UUID id, String ref, String status, BigDecimal amount, String currency,
                                 BigDecimal fee, BigDecimal fxRate, BigDecimal settlementAmount,
                                 String settlementCurrency, LocalDate valueDate, String reference,
                                 String beneficiaryName, UUID beneficiaryId, String debitAccountNumber,
                                 UUID debitAccountId, UUID companyId, String createdByName, String reason,
                                 Instant createdAt, Instant updatedAt, boolean awaitingMyApproval,
                                 int approvalCount, String bulkFileRef) {
    }

    public record ApprovalAction(String actorName, String decision, String comment,
                                 BigDecimal limitApplied, boolean halfRequired, Instant actedAt) {
    }

    public record Amendment(String actorName, List<ChangedField> changedFields, Instant changedAt) {
        public record ChangedField(String field, String from, String to) {
        }
    }

    public record Comment(String authorName, String authorRole, String text, Instant createdAt) {
    }

    public record Release(UUID id, UUID paymentId, String paymentRef, String paymentStatus,
                          String coreRef,
                          BigDecimal postedAmount, String responseCode,
                          String reason, int retryCount, String investigationStatus,
                          String investigationNotes, Instant attemptedAt, Instant releasedAt) {
    }

    public record TimelineEntry(Instant eventAt, String actorType, String actorName, String action,
                                String summary, String authBasis) {
    }

    public record ApprovalContext(boolean canApprove, boolean canReject, boolean canReturn,
                                  boolean canAmend, boolean canSubmit, BigDecimal myLimit,
                                  BigDecimal requiredAmount, boolean dualApproval, BigDecimal half,
                                  String companyDailyLimit, String utilisationUsed, String headroom,
                                  String baseCurrency) {
    }

    public record PaymentDetail(PaymentSummary summary, String beneficiaryAccountNumber,
                                String beneficiaryBankCode, String beneficiaryCurrency,
                                String amendedByName, List<Amendment> amendments,
                                List<ApprovalAction> approvals, List<Comment> comments, Release release,
                                List<TimelineEntry> timeline, ApprovalContext approvalContext) {
    }

    public record FeePreview(BigDecimal amount, String currency, BigDecimal fee, String rail,
                             BigDecimal flat, BigDecimal pct, BigDecimal min, BigDecimal max,
                             BigDecimal fxRate, BigDecimal settlementAmount, String settlementCurrency) {
    }

    public record Account(UUID id, String accountNumber, String currency, String product, String name,
                          BigDecimal balance, UUID companyId) {
    }

    public record Beneficiary(UUID id, String name, String accountNumber, String bankCode,
                              String currency, String status, Instant coolingUntil, boolean payable,
                              Instant createdAt, String createdByName, Instant lastAmendedAt) {
    }

    public record CompanyUser(UUID id, String email, String fullName, String role, String status,
                              BigDecimal approvalLimit, List<UUID> accountIds, Instant createdAt,
                              Instant updatedAt) {
    }

    public record FeeSchedule(String productType, String currency, BigDecimal flatFee,
                              BigDecimal pctFee, BigDecimal minFee, BigDecimal maxFee,
                              Instant effectiveFrom) {
    }

    public record FxRate(String currencyPair, BigDecimal rate, Instant effectiveAt) {
    }

    public record WebhookConfig(String url, String signingKey, boolean active, Instant updatedAt) {
    }

    public record Settings(List<FeeSchedule> feeSchedule, List<FxRate> fxRates, WebhookConfig webhook) {
    }

    public record FormLayout(int widgets, Object layout) {
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

    public record Reversal(UUID id, UUID paymentId, String paymentRef, String reason, String status,
                           String requestedByName, Instant requestedAt, String approvedByName,
                           Instant decidedAt, String coreRef, Instant postedAt) {
    }

    public record Dashboard(PendingApprovals approvals, Released released, Failed failed,
                            Utilisation utilisation, long unreadNotifications,
                            List<PaymentSummary> recentPayments, List<ActivityItem> recentActivity) {
        public record PendingApprovals(long total, long awaitingMe) {
        }

        public record Released(long count, BigDecimal value, String currency) {
        }

        public record Failed(long count) {
        }

        public record Utilisation(BigDecimal used, BigDecimal limit, BigDecimal headroom,
                                  String currency, int percent) {
        }

        public record ActivityItem(Instant eventAt, String actorName, String action, String summary) {
        }
    }

    public record OpsDashboard(long releasedTodayCount, BigDecimal releasedTodayValue,
                               long failedReleases, long investigatingReleases, long dueToday,
                               long pendingReversals, List<Release> recentReleases,
                               List<PaymentSummary> recentFailures) {
    }

    public record CompanyView(UUID id, String legalName, String registrationNumber, String status,
                              String baseCurrency, BigDecimal dailyLimit, long userCount,
                              UtilisationView utilisationToday, long pendingReversals) {
    }

    public record UtilisationView(BigDecimal used, BigDecimal limit, BigDecimal headroom,
                                  String currency, int percent) {
    }

    public record AdminDashboard(List<CompanyView> companies, long pendingReversals,
                                 long auditEvents24h, BigDecimal totalReleasedToday,
                                 List<Reversal> recentReversals) {
    }

    public record Notification(UUID id, String type, String message, String link,
                               Instant createdAt, Instant readAt) {
    }

    public record AuditEntry(UUID id, Instant eventAt, String actorType, String actorName,
                             String actorRole, String action, String resourceType, String resourceRef,
                             UUID companyId, String summary, Object before, Object after,
                             String authBasis) {
    }

    public record StatusLookup(String paymentRef, String status, BigDecimal amount, String currency,
                               BigDecimal fee, BigDecimal settlementAmount, LocalDate valueDate,
                               String debitAccountNumber, String beneficiaryAccountNumber,
                               Instant updatedAt) {
    }

    public record PageOfPayments(List<PaymentSummary> content, int page, int size, long totalElements,
                                 int totalPages) {
    }

    public record PageOfAudit(List<AuditEntry> content, int page, int size, long totalElements,
                              int totalPages) {
    }

    public record ErrorBody(Error error) {
        public record Error(String code, String message) {
        }
    }
}
