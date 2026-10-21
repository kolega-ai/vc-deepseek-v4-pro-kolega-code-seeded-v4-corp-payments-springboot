package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Dashboard aggregates for company users, bank ops and bank admin. */
@Service
public class DashboardService {

    private final PaymentRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final ReleaseRepository releaseRepository;
    private final ReversalRepository reversalRepository;
    private final AuditEventRepository auditEventRepository;
    private final LimitService limitService;
    private final NotificationService notifications;

    public DashboardService(PaymentRepository paymentRepository,
                            CompanyRepository companyRepository,
                            ReleaseRepository releaseRepository,
                            ReversalRepository reversalRepository,
                            AuditEventRepository auditEventRepository,
                            LimitService limitService,
                            NotificationService notifications) {
        this.paymentRepository = paymentRepository;
        this.companyRepository = companyRepository;
        this.releaseRepository = releaseRepository;
        this.reversalRepository = reversalRepository;
        this.auditEventRepository = auditEventRepository;
        this.limitService = limitService;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ company dashboard

    public record CompanyDashboard(
            long pendingApprovals, long awaitingMyApproval, long releasedTodayCount,
            BigDecimal releasedTodayValue, long failedReleases,
            LimitService.Utilisation utilisation, long unreadNotifications,
            List<Payment> recentPayments, List<AuditEvent> recentActivity) {
    }

    @Transactional(readOnly = true)
    public CompanyDashboard companyDashboard(AuthPrincipal actor) {
        if (!actor.isCompanyUser()) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Company scope required");
        }
        Company company = companyRepository.findById(actor.companyId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        UUID companyId = company.getId();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<Payment> pending = paymentRepository.findByCompanyIdAndStatusIn(companyId,
                List.of(PaymentStatus.PENDING_APPROVAL, PaymentStatus.PARTIALLY_APPROVED));
        long awaitingMe = pending.stream().filter(p -> couldApprove(actor, p)).count();

        List<Payment> releasedToday = paymentRepository.findAll(
                        (Specification<Payment>) (root, cq, cb) -> cb.and(
                                cb.equal(root.get("company").get("id"), companyId),
                                cb.equal(root.get("status"), PaymentStatus.RELEASED),
                                cb.equal(root.get("valueDate"), today)))
                .stream().toList();
        BigDecimal releasedValue = releasedToday.stream()
                .map(p -> p.getAmount().add(p.getFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long failed = paymentRepository.countByCompanyIdAndStatusIn(companyId,
                List.of(PaymentStatus.FAILED));

        LimitService.Utilisation utilisation = limitService.utilisation(companyId, company, today);
        long unread = notifications.unreadCount(actor.userId());
        List<Payment> recent = paymentRepository.findTop10ByCompanyIdOrderByCreatedAtDesc(companyId);
        List<AuditEvent> activity = auditEventRepository.findAll(
                        (Specification<AuditEvent>) (root, cq, cb) -> cb.equal(root.get("companyId"), companyId),
                        org.springframework.data.domain.PageRequest.of(0, 10,
                                Sort.by(Sort.Direction.DESC, "eventAt")))
                .getContent();

        return new CompanyDashboard(pending.size(), awaitingMe, releasedToday.size(),
                MoneyUtil.round(releasedValue, company.getBaseCurrency()), failed,
                utilisation, unread, recent, activity);
    }

    private boolean couldApprove(AuthPrincipal actor, Payment payment) {
        if (actor.role() != Role.COMPANY_CHECKER) {
            return false;
        }
        if (payment.getCreatedBy() != null && payment.getCreatedBy().getId().equals(actor.userId())) {
            return false;
        }
        boolean amendedByMe = payment.getAmendments().stream()
                .anyMatch(a -> a.getActor() != null && a.getActor().getId().equals(actor.userId()));
        if (amendedByMe) {
            return false;
        }
        BigDecimal half = MoneyUtil.halfUp(payment.getAmount(), payment.getCurrency());
        return actor.approvalLimit() != null && actor.approvalLimit().compareTo(half) >= 0;
    }

    // ------------------------------------------------------------------ ops dashboard

    public record OpsDashboard(long releasedTodayCount, BigDecimal releasedTodayValue,
                               long failedReleases, long investigatingReleases,
                               long dueToday, long pendingReversals,
                               List<Release> recentReleases, List<Payment> recentFailures) {
    }

    @Transactional(readOnly = true)
    public OpsDashboard opsDashboard(AuthPrincipal actor) {
        if (actor.role() != Role.BANK_OPS && actor.role() != Role.BANK_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Bank role required");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Payment> releasedToday = paymentRepository.findAll(
                        (Specification<Payment>) (root, cq, cb) -> cb.and(
                                cb.equal(root.get("status"), PaymentStatus.RELEASED),
                                cb.greaterThanOrEqualTo(root.get("updatedAt"), dayStart)))
                .stream().toList();
        BigDecimal value = releasedToday.stream().map(p -> p.getAmount().add(p.getFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long failed = paymentRepository.countByStatusIn(List.of(PaymentStatus.FAILED));
        long investigating = releaseRepository.countByInvestigationStatusIn(List.of(
                ReleaseInvestigationStatus.INVESTIGATING, ReleaseInvestigationStatus.EXCEPTION));
        long due = paymentRepository.countByStatusIn(List.of(PaymentStatus.APPROVED));
        long pendingReversals = reversalRepository.findByStatusOrderByRequestedAtAsc(ReversalStatus.REQUESTED).size();
        List<Release> recentReleases = releaseRepository.findAll(
                        org.springframework.data.domain.PageRequest.of(0, 10,
                                Sort.by(Sort.Direction.DESC, "attemptedAt")))
                .getContent();
        List<Payment> recentFailures = paymentRepository.findByStatusIn(
                List.of(PaymentStatus.FAILED)).stream().limit(10).toList();
        return new OpsDashboard(releasedToday.size(), MoneyUtil.round(value, "GBP"), failed,
                investigating, due, pendingReversals, recentReleases, recentFailures);
    }

    // ------------------------------------------------------------------ admin dashboard

    public record AdminDashboard(List<BankAdminService.CompanyStats> companies,
                                 long pendingReversals, long auditEvents24h,
                                 BigDecimal totalReleasedToday) {
    }

    @Transactional(readOnly = true)
    public AdminDashboard adminDashboard(AuthPrincipal actor) {
        if (actor.role() != Role.BANK_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "bank_admin required");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Payment> releasedToday = paymentRepository.findAll(
                        (Specification<Payment>) (root, cq, cb) -> cb.and(
                                cb.equal(root.get("status"), PaymentStatus.RELEASED),
                                cb.greaterThanOrEqualTo(root.get("updatedAt"), dayStart)))
                .stream().toList();
        BigDecimal total = releasedToday.stream().map(p -> p.getAmount().add(p.getFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long audit24h = auditEventRepository.count(
                (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("eventAt"),
                        Instant.now().minus(24, ChronoUnit.HOURS)));
        return new AdminDashboard(null, // companyStats filled by controller via BankAdminService
                reversalRepository.findByStatusOrderByRequestedAtAsc(ReversalStatus.REQUESTED).size(),
                audit24h, MoneyUtil.round(total, "GBP"));
    }
}
