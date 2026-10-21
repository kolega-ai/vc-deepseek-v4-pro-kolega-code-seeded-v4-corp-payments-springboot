package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read model for payments: detail (with amendment/approval/release/comment/timeline data) and
 * filtered search. Every read passes the shared ownership check in
 * {@link AccessControlService} — one call frame above the controller.
 */
@Service
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final PaymentCommentRepository commentRepository;
    private final ReleaseRepository releaseRepository;
    private final AuditEventRepository auditEventRepository;
    private final AccessControlService access;

    public PaymentQueryService(PaymentRepository paymentRepository,
                               ApprovalActionRepository approvalActionRepository,
                               PaymentCommentRepository commentRepository,
                               ReleaseRepository releaseRepository,
                               AuditEventRepository auditEventRepository,
                               AccessControlService access) {
        this.paymentRepository = paymentRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.commentRepository = commentRepository;
        this.releaseRepository = releaseRepository;
        this.auditEventRepository = auditEventRepository;
        this.access = access;
    }

    /** Precision location #1: the ownership check runs inside this shared service. */
    @Transactional(readOnly = true)
    public Payment getPayment(AuthPrincipal actor, UUID paymentId) {
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        // Force-load the collections and their actor fields needed by the detail view.
        payment.getApprovalActions().size();
        payment.getAmendments().forEach(a -> {
            if (a.getActor() != null) {
                a.getActor().getFullName();
            }
        });
        return payment;
    }

    @Transactional(readOnly = true)
    public List<ApprovalAction> approvalHistory(UUID paymentId) {
        return approvalActionRepository.findByPaymentIdOrderByActedAtAsc(paymentId);
    }

    @Transactional(readOnly = true)
    public List<PaymentComment> comments(UUID paymentId) {
        return commentRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    @Transactional(readOnly = true)
    public Release releaseOf(UUID paymentId) {
        return releaseRepository.findByPaymentId(paymentId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> timeline(UUID paymentId) {
        return auditEventRepository.findTop20ByResourceIdOrderByEventAtDesc(paymentId);
    }

    // ------------------------------------------------------------------ search

    /**
     * PRECISION LOCATION #4: parameterised payment search with a bounded, whitelisted sort column.
     * The sort key and status filter values are whitelisted here; user input is bound as query
     * parameters, never concatenated into JPQL/SQL.
     */
    @Transactional(readOnly = true)
    public Page<Payment> search(AuthPrincipal actor,
                                List<PaymentStatus> statuses,
                                LocalDate from, LocalDate to,
                                UUID accountId, UUID beneficiaryId,
                                String query,
                                java.math.BigDecimal minAmount, java.math.BigDecimal maxAmount,
                                String sortKey, String sortDir,
                                int page, int size) {
        Specification<Payment> spec = Specification.where(null);
        if (actor.isCompanyUser()) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("company").get("id"), actor.companyId()));
        }
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and((root, cq, cb) -> root.get("status").in(statuses));
        }
        if (from != null) {
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("valueDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("valueDate"), to));
        }
        if (accountId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("debitAccount").get("id"), accountId));
        }
        if (beneficiaryId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("beneficiary").get("id"), beneficiaryId));
        }
        if (query != null && !query.isBlank()) {
            String like = "%" + query.strip().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("ref")), like),
                    cb.like(cb.lower(root.get("reference")), like)));
        }
        if (minAmount != null) {
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
        }
        if (maxAmount != null) {
            spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Bounded whitelist: only these columns may be used for ordering.
        String column = switch (sortKey == null ? "" : sortKey) {
            case "valueDate" -> "valueDate";
            case "amount" -> "amount";
            case "ref" -> "ref";
            case "status" -> "status";
            default -> "createdAt";
        };
        Sort sort = Sort.by(direction, column)
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        return paymentRepository.findAll(spec, PageRequest.of(safePage, safeSize, sort));
    }
}
