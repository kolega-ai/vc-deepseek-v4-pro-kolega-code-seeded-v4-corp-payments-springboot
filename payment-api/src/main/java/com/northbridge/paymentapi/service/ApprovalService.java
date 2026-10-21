package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Maker-checker approval with dual-approval limits.
 *
 * <ul>
 *   <li>Only company_checker can approve, and only for their own company.</li>
 *   <li>A checker cannot approve a payment they created or amended, and cannot record both
 *       approvals of a two-approval payment.</li>
 *   <li>If any single active checker in the company has a limit ≥ amount, one approval by such a
 *       checker suffices (pending_approval→approved).</li>
 *   <li>Otherwise two approvals are required, each by a DIFFERENT checker whose limit is ≥ half
 *       (amount/2 rounded up to the minor unit): the first moves it to partially_approved, the
 *       second to approved.</li>
 *   <li>The limit is always read server-side from the acting user's record — a limit value
 *       submitted in a request is ignored because the request DTO has no such field.</li>
 * </ul>
 */
@Service
public class ApprovalService {

    private final PaymentRepository paymentRepository;
    private final CompanyUserRepository userRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final BulkFileLineRepository lineRepository;
    private final AccessControlService access;
    private final PaymentWorkflowService workflow;
    private final LimitService limitService;
    private final AuditService auditService;
    private final NotificationService notifications;

    public ApprovalService(PaymentRepository paymentRepository,
                           CompanyUserRepository userRepository,
                           ApprovalActionRepository approvalActionRepository,
                           BulkFileLineRepository lineRepository,
                           AccessControlService access,
                           PaymentWorkflowService workflow,
                           LimitService limitService,
                           AuditService auditService,
                           NotificationService notifications) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.lineRepository = lineRepository;
        this.access = access;
        this.workflow = workflow;
        this.limitService = limitService;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    public record ApprovalContext(BigDecimal limit, BigDecimal required, boolean dualApproval,
                                  BigDecimal half, String companyCurrency) {
    }

    @Transactional
    public ApprovalContext approvalContext(AuthPrincipal actor, UUID paymentId) {
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        CompanyUser checker = requireChecker(actor, payment);
        BigDecimal half = MoneyUtil.halfUp(payment.getAmount(), payment.getCurrency());
        boolean singleSufficientExists = !userRepository.findCheckersWithLimitAtLeast(
                payment.getCompany().getId(), Role.COMPANY_CHECKER, UserStatus.ACTIVE, payment.getAmount()).isEmpty();
        BigDecimal required = singleSufficientExists ? payment.getAmount() : half;
        return new ApprovalContext(checker.getApprovalLimit(), required,
                !singleSufficientExists, half, payment.getCompany().getBaseCurrency());
    }

    /** approve → approved or partially_approved, per the dual-approval rule. */
    @Transactional
    public void approve(AuthPrincipal actor, UUID paymentId, String comment) {
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        access.requireActiveCompany(payment.getCompany());
        CompanyUser checker = requireChecker(actor, payment);
        requireNotCreatorOrAmender(actor, payment);
        if (!workflow.isPayable(payment.getBeneficiary(), Instant.now())) {
            throw new ApiException(ApiException.Code.BENEFICIARY_NOT_PAYABLE,
                    "Beneficiary " + payment.getBeneficiary().getName() + " is not payable");
        }
        checkDailyLimit(payment);

        PaymentStatus from = payment.getStatus();
        BigDecimal limit = checker.getApprovalLimit();
        BigDecimal half = MoneyUtil.halfUp(payment.getAmount(), payment.getCurrency());
        boolean halfRequired;

        switch (from) {
            case PENDING_APPROVAL -> {
                boolean singleSufficientExists = !userRepository.findCheckersWithLimitAtLeast(
                        payment.getCompany().getId(), Role.COMPANY_CHECKER, UserStatus.ACTIVE,
                        payment.getAmount()).isEmpty();
                if (singleSufficientExists) {
                    requireLimit(limit, payment.getAmount(), payment.getCurrency(), checker);
                    halfRequired = false;
                    workflow.transition(payment, PaymentStatus.APPROVED, actor,
                            "PAYMENT_APPROVED", "Approved by single sufficient checker",
                            "checker_approve required=" + payment.getAmount() + " limit=" + limit);
                } else {
                    requireLimit(limit, half, payment.getCurrency(), checker);
                    halfRequired = true;
                    workflow.transition(payment, PaymentStatus.PARTIALLY_APPROVED, actor,
                            "PAYMENT_PARTIALLY_APPROVED",
                            "First of two approvals recorded (dual-approval path)",
                            "checker_approve required=" + half + " limit=" + limit);
                }
            }
            case PARTIALLY_APPROVED -> {
                // The second approval must come from a DIFFERENT checker than the first.
                boolean alreadyActed = payment.getApprovalActions().stream()
                        .anyMatch(a -> a.getDecision() == ApprovalAction.Decision.APPROVED
                                && a.getActor() != null && a.getActor().getId().equals(checker.getId()));
                if (alreadyActed) {
                    throw new ApiException(ApiException.Code.SELF_ACTION_FORBIDDEN,
                            "You cannot record both approvals of a two-approval payment");
                }
                requireLimit(limit, half, payment.getCurrency(), checker);
                halfRequired = true;
                workflow.transition(payment, PaymentStatus.APPROVED, actor,
                        "PAYMENT_APPROVED", "Second approval recorded (dual-approval path)",
                        "checker_approve required=" + half + " limit=" + limit);
            }
            default -> throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Payment " + payment.getRef() + " is not awaiting approval (status " + from + ")");
        }

        ApprovalAction action = new ApprovalAction(UUID.randomUUID(), payment, checker,
                ApprovalAction.Decision.APPROVED, trim(comment, 500), limit, halfRequired);
        payment.getApprovalActions().add(action); // persisted via the payment cascade

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            syncBulkLine(payment, BulkLineStatus.APPROVED, actor);
        }
    }

    @Transactional
    public void reject(AuthPrincipal actor, UUID paymentId, String comment) {
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        CompanyUser checker = requireChecker(actor, payment);
        requireNotCreatorOrAmender(actor, payment);
        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL
                && payment.getStatus() != PaymentStatus.PARTIALLY_APPROVED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Payment is not awaiting approval");
        }
        workflow.transition(payment, PaymentStatus.REJECTED, actor, "PAYMENT_REJECTED",
                "Payment rejected by checker", "checker_reject");
        payment.setReason(trim(comment, 200));
        payment.getApprovalActions().add(new ApprovalAction(UUID.randomUUID(), payment, checker,
                ApprovalAction.Decision.REJECTED, trim(comment, 500), checker.getApprovalLimit(), false));
        syncBulkLine(payment, BulkLineStatus.REJECTED, actor);
        notifyMaker(payment, NotificationType.PAYMENT_REJECTED,
                "Payment " + payment.getRef() + " was rejected");
    }

    @Transactional
    public void returnForAmendment(AuthPrincipal actor, UUID paymentId, String comment) {
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        CompanyUser checker = requireChecker(actor, payment);
        requireNotCreatorOrAmender(actor, payment);
        if (payment.getStatus() != PaymentStatus.PENDING_APPROVAL
                && payment.getStatus() != PaymentStatus.PARTIALLY_APPROVED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Payment is not awaiting approval");
        }
        workflow.transition(payment, PaymentStatus.RETURNED, actor, "PAYMENT_RETURNED",
                "Payment returned for amendment", "checker_return");
        payment.setReason(trim(comment, 200));
        payment.getApprovalActions().add(new ApprovalAction(UUID.randomUUID(), payment, checker,
                ApprovalAction.Decision.RETURNED, trim(comment, 500), checker.getApprovalLimit(), false));
        notifyMaker(payment, NotificationType.PAYMENT_RETURNED,
                "Payment " + payment.getRef() + " was returned for amendment");
    }

    // ------------------------------------------------------------------ guards

    private CompanyUser requireChecker(AuthPrincipal actor, Payment payment) {
        if (actor.role() != Role.COMPANY_CHECKER) {
            throw new ApiException(ApiException.Code.FORBIDDEN,
                    "Only the company_checker role can approve payments");
        }
        CompanyUser checker = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ApiException(ApiException.Code.UNAUTHORIZED, "Unknown user"));
        if (!checker.getCompany().getId().equals(payment.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "Payment not found");
        }
        return checker;
    }

    private void requireNotCreatorOrAmender(AuthPrincipal actor, Payment payment) {
        if (payment.getCreatedBy() != null && payment.getCreatedBy().getId().equals(actor.userId())) {
            throw new ApiException(ApiException.Code.SELF_APPROVAL_FORBIDDEN,
                    "You cannot approve a payment you created");
        }
        boolean amended = payment.getAmendments().stream()
                .anyMatch(a -> a.getActor() != null && a.getActor().getId().equals(actor.userId()));
        if (amended) {
            throw new ApiException(ApiException.Code.SELF_APPROVAL_FORBIDDEN,
                    "You cannot approve a payment you amended");
        }
    }

    private void requireLimit(BigDecimal limit, BigDecimal required, String currency, CompanyUser checker) {
        if (limit == null || limit.compareTo(required) < 0) {
            throw new ApiException(ApiException.Code.APPROVAL_LIMIT_EXCEEDED,
                    "Your approval limit (" + limit + ") does not cover the required " + required
                            + " for this payment");
        }
    }

    private void checkDailyLimit(Payment payment) {
        // Only value dates in the current UTC day count against the company daily limit.
        if (!payment.getValueDate().equals(java.time.LocalDate.now(java.time.ZoneOffset.UTC))) {
            return;
        }
        LimitService.Utilisation utilisation = limitService.utilisationToday(
                payment.getCompany().getId(), payment.getCompany());
        // The payment being approved would consume settlement + fee (converted to base currency).
        BigDecimal contribution = limitService.contribution(payment);
        if (utilisation.used().add(contribution).compareTo(utilisation.limit()) > 0) {
            throw new ApiException(ApiException.Code.DAILY_LIMIT_EXCEEDED,
                    "Approval would exceed the company daily limit: used " + utilisation.used()
                            + " + " + contribution + " of " + utilisation.limit() + " "
                            + utilisation.currency());
        }
    }

    private void syncBulkLine(Payment payment, BulkLineStatus status, AuthPrincipal actor) {
        lineRepository.findByPaymentId(payment.getId()).ifPresent(line -> {
            if (line.getStatus() == BulkLineStatus.VALID && status == BulkLineStatus.APPROVED) {
                line.setStatus(BulkLineStatus.APPROVED);
                lineRepository.save(line);
                auditService.audit(actor, "BULK_LINE_APPROVED", "bulk_line", line.getId(),
                        line.getBulkFile().getRef() + "#" + line.getLineNumber(),
                        "Bulk line approved", Map.of("status", "valid"), Map.of("status", "approved"));
            } else if (status == BulkLineStatus.REJECTED
                    && (line.getStatus() == BulkLineStatus.VALID || line.getStatus() == BulkLineStatus.APPROVED)) {
                line.setStatus(BulkLineStatus.REJECTED);
                lineRepository.save(line);
                auditService.audit(actor, "BULK_LINE_REJECTED", "bulk_line", line.getId(),
                        line.getBulkFile().getRef() + "#" + line.getLineNumber(),
                        "Bulk line rejected", null, Map.of("status", "rejected"));
            }
        });
    }

    private void notifyMaker(Payment payment, NotificationType type, String message) {
        if (payment.getCreatedBy() != null) {
            notifications.notify(payment.getCreatedBy().getId(), type, message,
                    "/payments/" + payment.getId());
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.strip().length() > max ? value.strip().substring(0, max) : value.strip();
    }
}
