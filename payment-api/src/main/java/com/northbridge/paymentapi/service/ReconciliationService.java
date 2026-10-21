package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.PaymentRepository;
import com.northbridge.paymentapi.repo.ReleaseRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconciliation between this tier's released payments and the vendor core's postings. The batch
 * module fetches candidate releases here, matches them against core postings, and reports
 * exceptions; exceptions surface on the release record as investigation status EXCEPTION.
 */
@Service
public class ReconciliationService {

    private final ReleaseRepository releaseRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final NotificationService notifications;

    public ReconciliationService(ReleaseRepository releaseRepository,
                                 PaymentRepository paymentRepository,
                                 AuditService auditService,
                                 NotificationService notifications) {
        this.releaseRepository = releaseRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    public record ReleaseCandidate(UUID paymentId, String paymentRef, String coreRef,
                                   java.math.BigDecimal postedAmount, String accountNumber) {
    }

    @Transactional(readOnly = true)
    public List<ReleaseCandidate> reconciliationCandidates() {
        return releaseRepository.findAll().stream()
                .filter(r -> r.getCoreRef() != null && r.getPayment().getStatus() == PaymentStatus.RELEASED)
                .map(r -> new ReleaseCandidate(r.getPayment().getId(), r.getPayment().getRef(),
                        r.getCoreRef(), r.getPostedAmount(),
                        r.getPayment().getDebitAccount().getAccountNumber()))
                .toList();
    }

    public record ExceptionReport(UUID paymentId, String coreRef, String reason) {
    }

    /** Batch reports reconciliation exceptions; releases are flagged for investigation. */
    @Transactional
    public int reportExceptions(List<ExceptionReport> exceptions) {
        int flagged = 0;
        for (ExceptionReport report : exceptions) {
            releaseRepository.findByPaymentId(report.paymentId()).ifPresent(release -> {
                if (release.getInvestigationStatus() != ReleaseInvestigationStatus.EXCEPTION) {
                    release.setInvestigationStatus(ReleaseInvestigationStatus.EXCEPTION);
                    if (release.getInvestigationNotes() == null || release.getInvestigationNotes().isBlank()) {
                        release.setInvestigationNotes(report.reason());
                    }
                    releaseRepository.save(release);
                    Payment payment = release.getPayment();
                    auditService.auditSystemForCompany("batch-reconciliation", payment.getCompany().getId(),
                            "RECONCILIATION_EXCEPTION", "release", release.getId(), payment.getRef(),
                            "Reconciliation exception: " + report.reason(),
                            Map.of("coreRef", report.coreRef() == null ? "" : report.coreRef()),
                            Map.of("investigationStatus", "EXCEPTION"),
                            "system=batch basis=reconciliation");
                    notifications.notifyBankUsers(Role.BANK_OPS, NotificationType.RECONCILIATION_EXCEPTION,
                            "Reconciliation exception on payment " + payment.getRef() + ": " + report.reason(),
                            "/ops/releases");
                }
            });
            flagged++;
        }
        return flagged;
    }

    @Transactional(readOnly = true)
    public List<Release> listReleases(AuthPrincipal actor, String status, String investigation) {
        if (actor.role() != Role.BANK_OPS && actor.role() != Role.BANK_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Bank role required");
        }
        var stream = releaseRepository.findAll().stream();
        if (status != null && !status.isBlank()) {
            stream = stream.filter(r -> r.getPayment().getStatus().name().equalsIgnoreCase(status));
        }
        if (investigation != null && !investigation.isBlank()) {
            stream = stream.filter(r -> r.getInvestigationStatus().name().equalsIgnoreCase(investigation));
        }
        return stream
                .sorted((a, b) -> b.getAttemptedAt().compareTo(a.getAttemptedAt()))
                .limit(200)
                .toList();
    }
}
