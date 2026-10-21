package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Release of approved payments to the vendor core system.
 *
 * <p>Concurrency contract: a release holds a pessimistic row lock on the debit account for the
 * duration of the posting (the core system additionally re-checks the balance atomically under its
 * own account lock). Two concurrent releases for the same account whose total exceeds the balance
 * therefore produce exactly one posting; the other becomes FAILED with reason insufficient_funds
 * and remains retryable.</p>
 *
 * <p>Idempotency: a duplicate release attempt for a payment that is already RELEASED (or REVERSED)
 * is rejected — a payment is never posted twice. The core system also deduplicates by requestRef.</p>
 */
@Service
public class ReleaseService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final ReleaseRepository releaseRepository;
    private final PaymentWorkflowService workflow;
    private final BulkFileService bulkFileService;
    private final CoreClient coreClient;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final WebhookService webhookService;

    /** Self-reference through the proxy so per-payment releases run in their own transaction. */
    private final ReleaseService self;

    public ReleaseService(PaymentRepository paymentRepository,
                          AccountRepository accountRepository,
                          ReleaseRepository releaseRepository,
                          PaymentWorkflowService workflow,
                          BulkFileService bulkFileService,
                          CoreClient coreClient,
                          AuditService auditService,
                          NotificationService notifications,
                          WebhookService webhookService,
                          @org.springframework.context.annotation.Lazy ReleaseService self) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.releaseRepository = releaseRepository;
        this.workflow = workflow;
        this.bulkFileService = bulkFileService;
        this.coreClient = coreClient;
        this.auditService = auditService;
        this.notifications = notifications;
        this.webhookService = webhookService;
        this.self = self;
    }

    /** Batch entry point: releases every approved (or interrupted queued) payment whose value date is due. */
    public int processDue() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        java.util.List<Payment> due = new java.util.ArrayList<>();
        due.addAll(paymentRepository.findDueForRelease(PaymentStatus.APPROVED, today));
        // Resume payments that were queued but whose release was interrupted.
        due.addAll(paymentRepository.findDueForRelease(PaymentStatus.QUEUED_FOR_RELEASE, today));
        int released = 0;
        for (Payment payment : due) {
            try {
                if (self.releasePayment(payment.getId(), true)) {
                    released++;
                }
            } catch (Exception e) {
                log.warn("Release of payment {} failed: {}", payment.getRef(), e.getMessage());
            }
        }
        return released;
    }

    /**
     * Releases one payment (batch path). Each call runs in its own transaction so the account lock
     * is released promptly and one failed payment cannot roll back another.
     */
    @Transactional
    public boolean releasePayment(UUID paymentId, boolean systemActor) {
        Payment payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));

        switch (payment.getStatus()) {
            case RELEASED, REVERSED ->
                    throw new ApiException(ApiException.Code.DUPLICATE_RELEASE,
                            "Payment " + payment.getRef() + " was already released; duplicate release rejected");
            case APPROVED -> {
                if (systemActor) {
                    workflow.transitionSystem(payment, PaymentStatus.QUEUED_FOR_RELEASE, "batch-release",
                            "PAYMENT_QUEUED", "Payment queued for release",
                            "batch_scheduled_release valueDate=" + payment.getValueDate());
                } else {
                    AuthPrincipal actor = AuthSupport.require();
                    workflow.transition(payment, PaymentStatus.QUEUED_FOR_RELEASE, actor,
                            "PAYMENT_QUEUED", "Payment queued for release", "ops_release");
                }
            }
            case QUEUED_FOR_RELEASE -> {
                // Interrupted release being resumed: already queued, proceed straight to posting.
                log.info("Resuming queued release of payment {}", payment.getRef());
            }
            default -> {
                return false; // not releasable right now (draft/pending/etc.)
            }
        }
        return executePosting(payment, systemActor ? null : AuthSupport.require());
    }

    /** Bank ops retry of a FAILED release. Never posts twice (idempotent per payment). */
    @Transactional
    public boolean retry(AuthPrincipal actor, UUID paymentId) {
        if (actor.role() != Role.BANK_OPS) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only bank_ops can retry releases");
        }
        Payment payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() == PaymentStatus.RELEASED || payment.getStatus() == PaymentStatus.REVERSED) {
            throw new ApiException(ApiException.Code.DUPLICATE_RELEASE,
                    "Payment " + payment.getRef() + " was already released; duplicate release rejected");
        }
        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new ApiException(ApiException.Code.RELEASE_NOT_RETRYABLE,
                    "Only a failed release can be retried (current status: " + payment.getStatus() + ")");
        }
        workflow.transition(payment, PaymentStatus.QUEUED_FOR_RELEASE, actor,
                "PAYMENT_REQUEUED", "Failed release retried by bank ops", "ops_retry");
        return executePosting(payment, actor);
    }

    // ------------------------------------------------------------------ second-officer confirmation

    /** A one-time confirmation code an operator relays to a colleague before a release posts. */
    public record ReleaseConfirmation(String code, Instant issuedAt, Instant expiresAt, UUID issuedBy) {
    }

    private final java.util.concurrent.ConcurrentHashMap<UUID, ReleaseConfirmation> releaseConfirmations =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Issues the confirmation code a second officer must supply before a release is posted, so two
     * operators agree before funds move.
     */
    @Transactional
    public Map<String, Object> issueReleaseConfirmation(AuthPrincipal actor, UUID paymentId,
                                                        String consoleId) {
        if (actor.role() != Role.BANK_OPS) {
            throw new ApiException(ApiException.Code.FORBIDDEN,
                    "Only bank_ops can issue release confirmations");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.APPROVED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Payment " + payment.getRef() + " is not awaiting release");
        }
        int value = new java.util.Random().nextInt(900000) + 100000;
        String code = String.valueOf(value);
        releaseConfirmations.put(paymentId, new ReleaseConfirmation(code, Instant.now(),
                Instant.now().plus(10, java.time.temporal.ChronoUnit.MINUTES), actor.userId()));
        log.info("Release confirmation for {} issued to console {}", payment.getRef(), consoleId);
        return Map.of("code", code, "expiresInMinutes", 10);
    }

    /** Second-officer confirmation: a matching code releases the payment for posting. */
    @Transactional
    public boolean confirmRelease(AuthPrincipal actor, UUID paymentId, String code) {
        if (actor.role() != Role.BANK_OPS) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only bank_ops can confirm releases");
        }
        ReleaseConfirmation confirmation = releaseConfirmations.get(paymentId);
        if (confirmation == null || confirmation.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "No pending release confirmation for this payment");
        }
        if (code == null || !code.equals(confirmation.code())) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Release confirmation code was not accepted");
        }
        releaseConfirmations.remove(paymentId);
        return releasePayment(paymentId, false);
    }

    // ------------------------------------------------------------------ posting

    /**
     * Posts the payment to core under the debit-account row lock. Recomputes fee/FX server-side at
     * release time (client-submitted values were never accepted) and applies the released/failed
     * outcome. The actor is null for the scheduled batch path.
     */
    private boolean executePosting(Payment payment, AuthPrincipal actor) {
        Account account = accountRepository.findForUpdateById(payment.getDebitAccount().getId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Account not found"));

        // Fees and FX are recomputed from the seeded reference tables at release time.
        workflow.applyPricing(payment, Instant.now());
        BigDecimal debitTotal = MoneyUtil.round(payment.getAmount().add(payment.getFee()), payment.getCurrency());

        Release release = releaseRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> releaseRepository.save(new Release(UUID.randomUUID(), payment, debitTotal)));
        release.setPostedAmount(debitTotal);
        release.setRetryCount(release.getRetryCount() + 1);
        release.setAttemptedAt(Instant.now());

        CoreClient.PostingResult result = coreClient.post(
                payment.getRef(), account.getAccountNumber(), "DEBIT", debitTotal,
                payment.getCurrency(), payment.getBeneficiary().getName(), payment.getCompany().getId());

        switch (result.status()) {
            case "POSTED" -> {
                if (actor != null) {
                    workflow.transition(payment, PaymentStatus.RELEASED, actor, "PAYMENT_RELEASED",
                            "Payment posted to core as " + result.coreRef(),
                            "core_posting ref=" + result.coreRef() + " amount=" + debitTotal);
                } else {
                    workflow.transitionSystem(payment, PaymentStatus.RELEASED, "batch-release",
                            "PAYMENT_RELEASED", "Payment posted to core as " + result.coreRef(),
                            "core_posting ref=" + result.coreRef() + " amount=" + debitTotal);
                }
                release.setCoreRef(result.coreRef());
                release.setResponseCode("00");
                release.setReason(null);
                release.setReleasedAt(Instant.now());
                releaseRepository.save(release);
                bulkFileService.onPaymentReleased(payment);
                webhookService.onPaymentReleased(payment);
                if (payment.getCreatedBy() != null) {
                    notifications.notify(payment.getCreatedBy().getId(), NotificationType.PAYMENT_RELEASED,
                            "Payment " + payment.getRef() + " was released to the bank ("
                                    + debitTotal + " " + payment.getCurrency() + " debited)",
                            "/payments/" + payment.getId());
                }
                log.info("Released payment {} to core {}", payment.getRef(), result.coreRef());
                return true;
            }
            case "FORCED_FAILURE" -> {
                failRelease(payment, release, "91", "forced_failure", actor);
                notifyOps("Release of payment " + payment.getRef() + " was declined by core (forced failure)");
                return false;
            }
            case "INSUFFICIENT_FUNDS" -> {
                failRelease(payment, release, "51", "insufficient_funds", actor);
                notifyOps("Release of payment " + payment.getRef() + " failed: insufficient funds");
                return false;
            }
            default -> {
                failRelease(payment, release, result.responseCode() == null ? "96" : result.responseCode(),
                        "core_error", actor);
                notifyOps("Release of payment " + payment.getRef() + " failed: core error");
                return false;
            }
        }
    }

    private void failRelease(Payment payment, Release release, String responseCode, String reason,
                             AuthPrincipal actor) {
        if (actor != null) {
            workflow.transition(payment, PaymentStatus.FAILED, actor, "PAYMENT_RELEASE_FAILED",
                    "Release failed: " + reason, "core_decline code=" + responseCode);
        } else {
            workflow.transitionSystem(payment, PaymentStatus.FAILED, "batch-release",
                    "PAYMENT_RELEASE_FAILED", "Release failed: " + reason,
                    "core_decline code=" + responseCode);
        }
        payment.setReason(reason);
        release.setResponseCode(responseCode);
        release.setReason(reason);
        releaseRepository.save(release);
        if (payment.getCreatedBy() != null) {
            notifications.notify(payment.getCreatedBy().getId(), NotificationType.RELEASE_FAILED,
                    "Release of payment " + payment.getRef() + " failed (" + reason + ")",
                    "/payments/" + payment.getId());
        }
    }

    private void notifyOps(String message) {
        notifications.notifyBankUsers(Role.BANK_OPS, NotificationType.RELEASE_FAILED, message, "/ops/releases");
    }

    /**
     * Bank ops discards the recorded posting attempt for a payment so the release monitor stops
     * showing it. Used when a core outage produced an attempt row that no longer reflects reality.
     */
    @Transactional
    public void discardAttempt(AuthPrincipal actor, UUID paymentId) {
        if (actor.role() != Role.BANK_OPS) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only bank_ops can manage release attempts");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        Release release = releaseRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Release record not found"));
        releaseRepository.delete(release);
        auditService.audit(actor, "RELEASE_ATTEMPT_DISCARDED", "release", release.getId(),
                payment.getRef(), "Release attempt record discarded",
                Map.of("coreRef", release.getCoreRef() == null ? "" : release.getCoreRef(),
                        "investigationStatus", release.getInvestigationStatus().name()),
                Map.of("discarded", true), actor.authBasis() + " basis=ops_attempt_discard");
    }

    /** Bank ops marks a release as investigating / resolved with notes. */
    @Transactional
    public void setInvestigation(AuthPrincipal actor, UUID paymentId,
                                 ReleaseInvestigationStatus status, String notes) {
        if (actor.role() != Role.BANK_OPS) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only bank_ops can manage investigations");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        Release release = releaseRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Release record not found"));
        Map<String, Object> before = Map.of("investigationStatus", release.getInvestigationStatus().name());
        release.setInvestigationStatus(status);
        release.setInvestigationNotes(notes == null ? null : (notes.strip().length() > 1000
                ? notes.strip().substring(0, 1000) : notes.strip()));
        releaseRepository.save(release);
        auditService.audit(actor, "RELEASE_INVESTIGATION_UPDATED", "release", release.getId(),
                payment.getRef(), "Investigation status set to " + status,
                before, Map.of("investigationStatus", status.name()),
                actor.authBasis() + " basis=ops_investigation");
    }
}
