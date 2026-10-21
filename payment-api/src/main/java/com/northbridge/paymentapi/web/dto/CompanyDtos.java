package com.northbridge.paymentapi.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTOs for company-scoped reference data and the company dashboard. */
public final class CompanyDtos {

    private CompanyDtos() {
    }

    public record AccountView(UUID id, String accountNumber, String currency, String product,
                              String name, BigDecimal balance, UUID companyId) {
    }

    public record BeneficiaryView(UUID id, String name, String accountNumber, String bankCode,
                                  String currency, String status, Instant coolingUntil,
                                  boolean payable, Instant createdAt, String createdByName,
                                  Instant lastAmendedAt) {
    }

    public record CompanyUserView(UUID id, String email, String fullName, String role, String status,
                                  BigDecimal approvalLimit, List<UUID> accountIds, Instant createdAt,
                                  Instant updatedAt) {
    }

    public record FeeScheduleView(String productType, String currency, BigDecimal flatFee,
                                  BigDecimal pctFee, BigDecimal minFee, BigDecimal maxFee,
                                  Instant effectiveFrom) {
    }

    public record FxRateView(String currencyPair, BigDecimal rate, Instant effectiveAt) {
    }

    public record WebhookConfigView(String url, String signingKey, boolean active, Instant updatedAt) {
    }

    public record UserProfile(UUID userId, String email, String fullName, String role,
                              UUID companyId, String companyName, String companyCurrency,
                              String companyStatus, BigDecimal approvalLimit, List<UUID> accountIds,
                              String userStatus, boolean canCreatePayments, boolean canApprove,
                              boolean canManageCompany, boolean isBankOps, boolean isBankAdmin,
                              BigDecimal companyDailyLimit) {
    }

    public record Dashboard(PendingApprovals approvals, Released released, Failed failed,
                            Utilisation utilisation, long unreadNotifications,
                            List<PaymentDtos.PaymentSummary> recentPayments,
                            List<ActivityItem> recentActivity) {
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
}
