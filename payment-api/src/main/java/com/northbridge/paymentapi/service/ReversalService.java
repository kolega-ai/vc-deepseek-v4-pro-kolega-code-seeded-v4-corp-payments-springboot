package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.repo.PaymentRepository;
import com.northbridge.paymentapi.repo.ReversalRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.state.PaymentStateMachine;
import com.northbridge.paymentapi.state.ReversalStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reversals of released payments. bank_ops requests, bank_admin approves/declines (a distinct
 * right from customer-payment approval), and the batch module posts the approved reversal to the
 * core system. A reversal reaching POSTED moves the payment to REVERSED, which restores its
 * contribution to the company's daily headroom.
 */
@Service
public class ReversalService {

    private final ReversalRepository reversalRepository;
    private final PaymentRepository paymentRepository;
    private final CompanyUserRepository userRepository;
    private final PaymentWorkflowService workflow;
    private final CoreClient coreClient;
    private final AuditService auditService;
    private final NotificationService notifications;

    /** Self-reference through the proxy so each reversal posts in its own transaction. */
    private final ReversalService self;

    public ReversalService(ReversalRepository reversalRepository,
                           PaymentRepository paymentRepository,
                           CompanyUserRepository userRepository,
                           PaymentWorkflowService workflow,
                           CoreClient coreClient,
                           AuditService auditService,
                           NotificationService notifications,
                           @org.springframework.context.annotation.Lazy ReversalService self) {
        this.reversalRepository = reversalRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.workflow = workflow;
        this.coreClient = coreClient;
        this.auditService = auditService;
        this.notifications = notifications;
        this.self = self;
    }

    @Transactional
    public Reversal request(AuthPrincipal actor, UUID paymentId, String reason) {
        if (actor.role() != Role.BANK_OPS) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only bank_ops can request reversals");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != PaymentStatus.RELEASED) {
            throw new ApiException(ApiException.Code.REVERSAL_NOT_ALLOWED,
                    "Only a released payment can be reversed (status: " + payment.getStatus() + ")");
        }
        if (reversalRepository.findByPaymentId(paymentId).isPresent()) {
            throw new ApiException(ApiException.Code.DUPLICATE, "A reversal already exists for this payment");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Reversal reason is required (max 500)");
        }
        // The requester is resolved from the actor's own user record — never from the request body.
        CompanyUser requester = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ApiException(ApiException.Code.UNAUTHORIZED, "Unknown user"));
        Reversal reversal = new Reversal(UUID.randomUUID(), payment, reason.strip(), requester);
        reversalRepository.save(reversal);
        auditService.audit(actor, "REVERSAL_REQUESTED", "reversal", reversal.getId(), payment.getRef(),
                "Reversal requested: " + reason.strip(), null, Map.of("status", "requested"),
                actor.authBasis() + " basis=ops_reversal_request");
        notifications.notifyBankUsers(Role.BANK_ADMIN, NotificationType.REVERSAL_REQUESTED,
                "Reversal requested for payment " + payment.getRef() + ": " + reason.strip(),
                "/admin/reversals");
        return reversal;
    }

    @Transactional
    public Reversal decide(AuthPrincipal actor, UUID reversalId, boolean approve) {
        if (actor.role() != Role.BANK_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN,
                    "Only bank_admin can approve or decline reversals (a right distinct from customer-payment approval)");
        }
        Reversal reversal = reversalRepository.findById(reversalId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Reversal not found"));
        if (reversal.getStatus() != ReversalStatus.REQUESTED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Reversal is not awaiting a decision");
        }
        ReversalStatus target = approve ? ReversalStatus.APPROVED : ReversalStatus.DECLINED;
        ReversalStateMachine.require(reversal.getStatus(), target);
        reversal.setStatus(target);
        reversal.setDecidedAt(Instant.now());
        reversal.setApprovedBy(userRepository.findById(actor.userId()).orElse(null));
        reversalRepository.save(reversal);
        auditService.audit(actor, approve ? "REVERSAL_APPROVED" : "REVERSAL_DECLINED", "reversal",
                reversal.getId(), reversal.getPayment().getRef(),
                approve ? "Reversal approved" : "Reversal declined",
                Map.of("status", "requested"), Map.of("status", target.name()),
                actor.authBasis() + " basis=bank_admin_reversal_decision");
        if (approve && reversal.getPayment().getCreatedBy() != null) {
            notifications.notify(reversal.getPayment().getCreatedBy().getId(),
                    NotificationType.REVERSAL_APPROVED,
                    "A reversal of your payment " + reversal.getPayment().getRef() + " was approved",
                    "/payments/" + reversal.getPayment().getId());
        }
        return reversal;
    }

    /** Batch entry point: posts approved reversals to core and marks the payments reversed. */
    public int postDue() {
        List<Reversal> approved = reversalRepository.findByStatusOrderByRequestedAtAsc(ReversalStatus.APPROVED);
        int posted = 0;
        for (Reversal reversal : approved) {
            try {
                if (self.post(reversal)) {
                    posted++;
                }
            } catch (Exception ignored) {
                // A failed reversal posting is retried on the next batch cycle; core dedupes by requestRef.
            }
        }
        return posted;
    }

    @Transactional
    public boolean post(Reversal reversal) {
        Payment payment = paymentRepository.findForUpdateById(reversal.getPayment().getId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        if (reversal.getStatus() != ReversalStatus.APPROVED) {
            return false;
        }
        if (payment.getStatus() != PaymentStatus.RELEASED) {
            // Already reversed — treat as done (idempotent), never post twice.
            return false;
        }
        BigDecimal creditAmount = MoneyUtil.round(
                payment.getAmount().add(payment.getFee()), payment.getCurrency());
        CoreClient.PostingResult result = coreClient.post(
                "REV-" + payment.getRef(), payment.getDebitAccount().getAccountNumber(),
                "REVERSAL_CREDIT", creditAmount, payment.getCurrency(),
                payment.getBeneficiary().getName(), payment.getCompany().getId());
        if (!result.posted()) {
            return false; // retry on a later cycle
        }
        ReversalStateMachine.require(reversal.getStatus(), ReversalStatus.POSTED);
        reversal.setStatus(ReversalStatus.POSTED);
        reversal.setCoreRef(result.coreRef());
        reversal.setPostedAt(Instant.now());
        reversalRepository.save(reversal);

        // released → reversed restores the payment's contribution to daily headroom.
        PaymentStateMachine.require(payment.getStatus(), PaymentStatus.REVERSED);
        payment.setStatus(PaymentStatus.REVERSED);
        payment.setUpdatedAt(Instant.now());
        paymentRepository.save(payment);
        auditService.auditSystem("batch-release", "REVERSAL_POSTED", "reversal", reversal.getId(),
                payment.getRef(), "Reversal posted to core as " + result.coreRef(),
                Map.of("status", "approved", "paymentStatus", "released"),
                Map.of("status", "posted", "paymentStatus", "reversed"),
                "system=batch basis=reversal_posting headroom=restored");
        if (reversal.getRequestedBy() != null) {
            notifications.notify(reversal.getRequestedBy().getId(), NotificationType.REVERSAL_POSTED,
                    "Reversal of payment " + payment.getRef() + " has posted; funds restored",
                    "/ops/releases");
        }
        return true;
    }
}
