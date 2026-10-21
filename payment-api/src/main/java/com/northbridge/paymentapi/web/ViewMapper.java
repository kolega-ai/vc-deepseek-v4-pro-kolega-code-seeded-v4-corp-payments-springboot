package com.northbridge.paymentapi.web;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.service.ApprovalService;
import com.northbridge.paymentapi.service.LimitService;
import com.northbridge.paymentapi.service.MoneyUtil;
import com.northbridge.paymentapi.service.PaymentWorkflowService;
import com.northbridge.paymentapi.web.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Maps domain entities to API view DTOs. */
@Component
public class ViewMapper {

    private final ApprovalService approvalService;
    private final LimitService limitService;
    private final com.northbridge.paymentapi.repo.ApprovalActionRepository approvalActionRepository;

    public ViewMapper(ApprovalService approvalService, LimitService limitService,
                      com.northbridge.paymentapi.repo.ApprovalActionRepository approvalActionRepository) {
        this.approvalService = approvalService;
        this.limitService = limitService;
        this.approvalActionRepository = approvalActionRepository;
    }

    public PaymentDtos.PaymentSummary summary(Payment p, AuthPrincipal viewer) {
        boolean awaitingMe = viewer != null && p.getStatus() == PaymentStatus.PENDING_APPROVAL
                || viewer != null && p.getStatus() == PaymentStatus.PARTIALLY_APPROVED
                && couldApprove(viewer, p);
        return new PaymentDtos.PaymentSummary(
                p.getId(), p.getRef(), p.getStatus().name(), p.getAmount(), p.getCurrency(),
                p.getFee(), p.getFxRate(), p.getSettlementAmount(),
                p.getBeneficiary().getCurrency(), p.getValueDate(), p.getReference(),
                p.getBeneficiary().getName(), p.getBeneficiary().getId(),
                p.getDebitAccount().getAccountNumber(), p.getDebitAccount().getId(),
                p.getCompany().getId(),
                p.getCreatedBy() == null ? null : p.getCreatedBy().getFullName(),
                p.getReason(), p.getCreatedAt(), p.getUpdatedAt(), awaitingMe,
                (int) approvalActionRepository.countByPaymentIdAndDecision(p.getId(),
                        ApprovalAction.Decision.APPROVED),
                p.getBulkFile() == null ? null : p.getBulkFile().getRef());
    }

    private boolean couldApprove(AuthPrincipal viewer, Payment p) {
        if (viewer == null || viewer.role() != Role.COMPANY_CHECKER) {
            return false;
        }
        if (p.getCreatedBy() != null && p.getCreatedBy().getId().equals(viewer.userId())) {
            return false;
        }
        boolean amendedByMe = p.getAmendments().stream()
                .anyMatch(a -> a.getActor() != null && a.getActor().getId().equals(viewer.userId()));
        if (amendedByMe) {
            return false;
        }
        BigDecimal half = MoneyUtil.halfUp(p.getAmount(), p.getCurrency());
        return viewer.approvalLimit() != null && viewer.approvalLimit().compareTo(half) >= 0;
    }

    public PaymentDtos.PaymentDetail detail(Payment p, List<ApprovalAction> approvals,
                                            List<PaymentComment> comments, Release release,
                                            List<AuditEvent> timeline, AuthPrincipal viewer) {
        PaymentDtos.PaymentSummary summary = summary(p, viewer);
        var amendmentViews = p.getAmendments().stream()
                .map(a -> new PaymentDtos.AmendmentView(
                        a.getActor() == null ? "system" : a.getActor().getFullName(),
                        a.getChangedFields().stream()
                                .map(f -> new PaymentDtos.AmendmentView.ChangedFieldView(
                                        f.field(), f.from(), f.to()))
                                .toList(),
                        a.getChangedAt()))
                .toList();
        var approvalViews = approvals.stream()
                .map(a -> new PaymentDtos.ApprovalActionView(
                        a.getActor() == null ? "system" : a.getActor().getFullName(),
                        a.getDecision().name(), a.getComment(), a.getLimitApplied(),
                        a.isHalfRequired(), a.getActedAt()))
                .toList();
        var commentViews = comments.stream()
                .map(c -> new PaymentDtos.CommentView(
                        c.getAuthor().getFullName(), c.getAuthor().getRole().apiName(),
                        c.getText(), c.getCreatedAt()))
                .toList();
        PaymentDtos.ReleaseView releaseView = null;
        if (release != null) {
            releaseView = new PaymentDtos.ReleaseView(release.getId(), release.getPayment().getId(),
                    release.getPayment().getRef(), release.getPayment().getStatus().name(),
                    release.getCoreRef(),
                    release.getPostedAmount(), release.getResponseCode(), release.getReason(),
                    release.getRetryCount(), release.getInvestigationStatus().name(),
                    release.getInvestigationNotes(), release.getAttemptedAt(), release.getReleasedAt());
        }
        var timelineViews = timeline.stream()
                .map(e -> new PaymentDtos.TimelineEntry(e.getEventAt(),
                        e.getActorType().name(), e.getActorName(), e.getAction(), e.getSummary(),
                        e.getAuthBasis()))
                .toList();
        return new PaymentDtos.PaymentDetail(summary,
                p.getBeneficiary().getAccountNumber(), p.getBeneficiary().getBankCode(),
                p.getBeneficiary().getCurrency(),
                p.getAmendedBy() == null ? null : p.getAmendedBy().getFullName(),
                amendmentViews, approvalViews, commentViews, releaseView, timelineViews,
                approvalContext(p, viewer));
    }

    private PaymentDtos.ApprovalContextView approvalContext(Payment p, AuthPrincipal viewer) {
        if (viewer == null) {
            return new PaymentDtos.ApprovalContextView(false, false, false, false, false,
                    null, null, false, null, null, null, null, null);
        }
        boolean awaiting = p.getStatus() == PaymentStatus.PENDING_APPROVAL
                || p.getStatus() == PaymentStatus.PARTIALLY_APPROVED;
        boolean canApprove = awaiting && viewer.role() == Role.COMPANY_CHECKER && couldApprove(viewer, p);
        boolean canRejectOrReturn = awaiting && viewer.role() == Role.COMPANY_CHECKER
                && viewer.companyId() != null && viewer.companyId().equals(p.getCompany().getId())
                && (p.getCreatedBy() == null || !p.getCreatedBy().getId().equals(viewer.userId()));
        boolean canAmend = p.getStatus() == PaymentStatus.RETURNED && viewer.role() == Role.COMPANY_MAKER;
        boolean canSubmit = p.getStatus() == PaymentStatus.DRAFT && viewer.role() == Role.COMPANY_MAKER;
        BigDecimal myLimit = viewer.role() == Role.COMPANY_CHECKER ? viewer.approvalLimit() : null;
        BigDecimal half = MoneyUtil.halfUp(p.getAmount(), p.getCurrency());
        boolean dual = false;
        BigDecimal required = p.getAmount();
        try {
            ApprovalService.ApprovalContext ctx = approvalService.approvalContext(viewer, p.getId());
            dual = ctx.dualApproval();
            required = ctx.required();
            half = ctx.half();
        } catch (RuntimeException ignored) {
            // context is best-effort for display; authorization is enforced on the action itself
        }
        LimitService.Utilisation util = null;
        try {
            if (viewer.isCompanyUser()) {
                util = limitService.utilisationToday(p.getCompany().getId(), p.getCompany());
            }
        } catch (RuntimeException ignored) {
            // display only
        }
        return new PaymentDtos.ApprovalContextView(canApprove, canRejectOrReturn, canRejectOrReturn,
                canAmend, canSubmit, myLimit, required, dual, half,
                p.getCompany().getDailyLimit().toPlainString(),
                util == null ? null : util.used().toPlainString(),
                util == null ? null : util.headroom().toPlainString(),
                p.getCompany().getBaseCurrency());
    }

    public CompanyDtos.BeneficiaryView beneficiary(Beneficiary b) {
        return new CompanyDtos.BeneficiaryView(b.getId(), b.getName(), b.getAccountNumber(),
                b.getBankCode(), b.getCurrency(), b.getStatus().name(), b.getCoolingUntil(),
                b.getStatus() == BeneficiaryStatus.ACTIVE
                        && (b.getCoolingUntil() == null || !b.getCoolingUntil().isAfter(java.time.Instant.now())),
                b.getCreatedAt(), b.getCreatedBy() == null ? null : b.getCreatedBy().getFullName(),
                b.getLastAmendedAt());
    }

    public PaymentDtos.BulkLine bulkLine(BulkFileLine l) {
        return new PaymentDtos.BulkLine(l.getId(), l.getLineNumber(), l.getDebitAccountNumber(),
                l.getBeneficiaryAccountNumber(), l.getBankCode(), l.getAmount(), l.getValueDate(),
                l.getReference(), l.getStatus() == null ? null : l.getStatus().name(),
                l.getErrorMessage(), l.getPayment() == null ? null : l.getPayment().getId(),
                l.getPayment() == null ? null : l.getPayment().getRef());
    }

    public PaymentDtos.BulkFile bulkFile(BulkFile f, List<BulkFileLine> lines) {
        return new PaymentDtos.BulkFile(f.getId(), f.getRef(), f.getFileName(), f.getStatus().name(),
                f.getDeclaredTotal(), f.getDeclaredCount(), f.getParsedCount(), f.getValidCount(),
                f.getInvalidCount(), f.getUploadedAt(),
                f.getUploadedBy() == null ? null : f.getUploadedBy().getFullName(),
                f.getProcessedAt(), lines.stream().map(this::bulkLine).toList());
    }

    public PaymentDtos.ReversalView reversal(Reversal r) {
        return new PaymentDtos.ReversalView(r.getId(), r.getPayment().getId(), r.getPayment().getRef(),
                r.getReason(), r.getStatus().name(),
                r.getRequestedBy() == null ? null : r.getRequestedBy().getFullName(),
                r.getRequestedAt(), r.getApprovedBy() == null ? null : r.getApprovedBy().getFullName(),
                r.getDecidedAt(), r.getCoreRef(), r.getPostedAt());
    }

    public CompanyDtos.CompanyUserView user(CompanyUser u) {
        return new CompanyDtos.CompanyUserView(u.getId(), u.getEmail(), u.getFullName(),
                u.getRole().apiName(), u.getStatus().name(), u.getApprovalLimit(),
                u.getAssignedAccounts().stream().map(BaseEntity::getId).sorted().toList(),
                u.getCreatedAt(), u.getUpdatedAt());
    }

    public BankDtos.AuditView audit(AuditEvent e) {
        return new BankDtos.AuditView(e.getId(), e.getEventAt(), e.getActorType().name(),
                e.getActorName(), e.getActorRole(), e.getAction(), e.getResourceType(),
                e.getResourceRef(), e.getCompanyId(), e.getSummary(), e.getBeforeState(),
                e.getAfterState(), e.getAuthBasis());
    }

    public CommonDtos.NotificationView notification(Notification n) {
        return new CommonDtos.NotificationView(n.getId(), n.getType().name(), n.getMessage(),
                n.getLink(), n.getCreatedAt(), n.getReadAt());
    }

    public PaymentDtos.FeePreview feePreview(PaymentWorkflowService.FeePreview preview) {
        return new PaymentDtos.FeePreview(preview.amount(), preview.currency(), preview.fee(),
                preview.rail().name(), preview.breakdown().flat(), preview.breakdown().pct(),
                preview.breakdown().min(), preview.breakdown().max(), preview.fxRate(),
                preview.settlementAmount(), preview.settlementCurrency());
    }

    public BankDtos.CompanyView company(Company c, LimitService.Utilisation util, long userCount,
                                        long pendingReversals) {
        return new BankDtos.CompanyView(c.getId(), c.getLegalName(), c.getRegistrationNumber(),
                c.getStatus().name(), c.getBaseCurrency(), c.getDailyLimit(), userCount,
                new BankDtos.UtilisationView(util.used(), util.limit(), util.headroom(),
                        util.currency(), util.percent()),
                pendingReversals);
    }
}
