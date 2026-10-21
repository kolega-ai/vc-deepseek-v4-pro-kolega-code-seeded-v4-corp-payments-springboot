package com.northbridge.paymentapi.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTOs for bank ops / bank admin. */
public final class BankDtos {

    private BankDtos() {
    }

    public record OpsDashboard(long releasedTodayCount, BigDecimal releasedTodayValue,
                               long failedReleases, long investigatingReleases, long dueToday,
                               long pendingReversals, List<PaymentDtos.ReleaseView> recentReleases,
                               List<PaymentDtos.PaymentSummary> recentFailures) {
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
                                 List<PaymentDtos.ReversalView> recentReversals) {
    }

    public record AuditView(UUID id, Instant eventAt, String actorType, String actorName,
                            String actorRole, String action, String resourceType, String resourceRef,
                            UUID companyId, String summary, Object before, Object after,
                            String authBasis) {
    }
}
