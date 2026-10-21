package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.PaymentComment;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.repo.PaymentCommentRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** User-generated comments/notes on payments, visible to the payment's company. */
@Service
public class CommentService {

    private final PaymentCommentRepository commentRepository;
    private final CompanyUserRepository userRepository;
    private final AccessControlService access;
    private final AuditService auditService;
    private final PaymentNoteReviewService noteReview;

    public CommentService(PaymentCommentRepository commentRepository,
                          CompanyUserRepository userRepository,
                          AccessControlService access,
                          AuditService auditService,
                          PaymentNoteReviewService noteReview) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.auditService = auditService;
        this.noteReview = noteReview;
    }

    @Transactional
    public PaymentComment add(AuthPrincipal actor, UUID paymentId, String text) {
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        if (!actor.isCompanyUser() || !actor.companyId().equals(payment.getCompany().getId())) {
            throw new ApiException(ApiException.Code.FORBIDDEN,
                    "Only company users can comment on their payments");
        }
        String clean = text == null ? "" : text.strip();
        if (clean.isBlank() || clean.length() > 1000) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Comment text is required (max 1000)");
        }
        var author = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ApiException(ApiException.Code.UNAUTHORIZED, "Unknown user"));
        PaymentComment comment = new PaymentComment(UUID.randomUUID(), payment, author, clean);
        commentRepository.save(comment);
        auditService.audit(actor, "PAYMENT_COMMENT_ADDED", "payment", payment.getId(), payment.getRef(),
                "Comment added", null, null);
        noteReview.review(payment, clean);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<PaymentComment> list(UUID paymentId) {
        return commentRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }
}
