package com.northbridge.paymentapi.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request DTOs. Security-relevant fields (fee, fxRate, settlementAmount, status, company,
 * approvalLimit, ownership) are deliberately ABSENT so mass-assignment of those fields is
 * impossible: any submitted value for them is ignored by Jackson. */
public final class Requests {

    private Requests() {
    }

    public record CreatePaymentRequest(UUID debitAccountId, UUID beneficiaryId,
                                       BigDecimal amount, LocalDate valueDate, String reference) {
    }

    public record ApprovalRequest(String comment) {
    }

    public record AmendmentRequest(BigDecimal amount, LocalDate valueDate, String reference,
                                   UUID beneficiaryId) {
    }

    public record CreateBeneficiaryRequest(String name, String accountNumber, String bankCode,
                                           String currency) {
    }

    public record UpdateBeneficiaryRequest(String name, String accountNumber, String bankCode,
                                           String currency) {
    }

    public record RawBulkLine(int lineNumber, String content) {
    }

    public record CreateBulkFileRequest(String fileName, BigDecimal declaredTotal, int declaredCount,
                                        List<RawBulkLine> lines) {
    }

    public record InviteUserRequest(String email, String fullName, String role,
                                    BigDecimal approvalLimit, List<UUID> accountIds) {
    }

    public record UpdateUserRequest(String fullName, String role, BigDecimal approvalLimit,
                                    List<UUID> accountIds) {
    }

    public record CreateCompanyRequest(String legalName, String registrationNumber,
                                       String baseCurrency, BigDecimal dailyLimit) {
    }

    public record LimitChangeRequest(BigDecimal dailyLimit, String reason) {
    }

    public record CreateAccountRequest(UUID companyId, String accountNumber, String currency,
                                       String product, BigDecimal openingBalance) {
    }

    public record ReversalRequest(String reason) {
    }

    public record InvestigationRequest(String investigationStatus, String notes) {
    }

    public record CommentRequest(String text) {
    }

    public record WebhookConfigRequest(String url, String secret, boolean active) {
    }

    public record FormLayoutRequest(String layout) {
    }

    public record SealedInstructionRequest(String envelope) {
    }

    public record PricingQuoteRequest(BigDecimal amount, String currency) {
    }

    public record CoolingWaiverRequest(String reason) {
    }

    public record StaffVerifyRequest(String officerId) {
    }

    public record ExceptionEntry(UUID paymentId, String coreRef, String reason) {
    }

    public record ReconciliationReportRequest(List<ExceptionEntry> exceptions) {
    }

    public record PaymentLookupRequest(UUID companyId, String paymentRef) {
    }

    public record DeliveryOutcomeRequest(String status, String responseCode) {
    }

    public record ActivateUserRequest(UUID userId) {
    }
}
