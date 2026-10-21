package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.PaymentStatus;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.ApprovalService;
import com.northbridge.paymentapi.service.CommentService;
import com.northbridge.paymentapi.service.PaymentQueryService;
import com.northbridge.paymentapi.service.PaymentWorkflowService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.PaymentDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payment endpoints for the portal tier. The controller performs NO ownership checks itself —
 * every read/write goes through services that enforce company scope and role one call frame below
 * (see PaymentQueryService/AccessControlService).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentQueryService queryService;
    private final PaymentWorkflowService workflow;
    private final ApprovalService approvalService;
    private final CommentService commentService;
    private final ViewMapper mapper;

    public PaymentController(PaymentQueryService queryService,
                             PaymentWorkflowService workflow,
                             ApprovalService approvalService,
                             CommentService commentService,
                             ViewMapper mapper) {
        this.queryService = queryService;
        this.workflow = workflow;
        this.approvalService = approvalService;
        this.commentService = commentService;
        this.mapper = mapper;
    }

    @GetMapping
    public Page<PaymentDtos.PaymentSummary> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID beneficiaryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) java.math.BigDecimal minAmount,
            @RequestParam(required = false) java.math.BigDecimal maxAmount,
            @RequestParam(required = false, defaultValue = "createdAt") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        AuthPrincipal actor = AuthSupport.require();
        List<PaymentStatus> statuses = null;
        if (status != null && !status.isBlank()) {
            statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> PaymentStatus.valueOf(s.toUpperCase()))
                    .toList();
        }
        return queryService.search(actor, statuses, from, to, accountId, beneficiaryId, q,
                minAmount, maxAmount, sort, dir, page, size).map(p -> mapper.summary(p, actor));
    }

    @PostMapping("/preview")
    public PaymentDtos.FeePreview preview(@RequestBody Requests.CreatePaymentRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        return mapper.feePreview(workflow.preview(actor, request.debitAccountId(),
                request.beneficiaryId(), request.amount()));
    }

    @PostMapping
    public PaymentDtos.PaymentSummary create(@RequestBody Requests.CreatePaymentRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        UUID id = workflow.createDraft(actor, request.debitAccountId(), request.beneficiaryId(),
                request.amount(), request.valueDate(), request.reference());
        return mapper.summary(queryService.getPayment(actor, id), actor);
    }

    @GetMapping("/{id}")
    public PaymentDtos.PaymentDetail detail(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        Payment payment = queryService.getPayment(actor, id);
        return mapper.detail(payment,
                queryService.approvalHistory(id),
                queryService.comments(id),
                queryService.releaseOf(id),
                queryService.timeline(id),
                actor);
    }

    @PostMapping("/{id}/submit")
    public PaymentDtos.PaymentSummary submit(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        workflow.submit(actor, id);
        return mapper.summary(queryService.getPayment(actor, id), actor);
    }

    @PostMapping("/{id}/approve")
    public PaymentDtos.PaymentSummary approve(@PathVariable UUID id,
                                              @RequestBody(required = false) Requests.ApprovalRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        approvalService.approve(actor, id, request == null ? null : request.comment());
        return mapper.summary(queryService.getPayment(actor, id), actor);
    }

    @PostMapping("/{id}/reject")
    public PaymentDtos.PaymentSummary reject(@PathVariable UUID id,
                                             @RequestBody(required = false) Requests.ApprovalRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        approvalService.reject(actor, id, request == null ? null : request.comment());
        return mapper.summary(queryService.getPayment(actor, id), actor);
    }

    @PostMapping("/{id}/return")
    public PaymentDtos.PaymentSummary returnForAmendment(
            @PathVariable UUID id,
            @RequestBody(required = false) Requests.ApprovalRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        approvalService.returnForAmendment(actor, id, request == null ? null : request.comment());
        return mapper.summary(queryService.getPayment(actor, id), actor);
    }

    @PostMapping("/{id}/amend")
    public PaymentDtos.PaymentSummary amend(@PathVariable UUID id,
                                            @RequestBody Requests.AmendmentRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        workflow.amend(actor, id, request.amount(), request.valueDate(), request.reference(),
                request.beneficiaryId());
        return mapper.summary(queryService.getPayment(actor, id), actor);
    }

    @PostMapping("/{id}/comments")
    public List<PaymentDtos.CommentView> comment(@PathVariable UUID id,
                                                 @RequestBody Requests.CommentRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        queryService.getPayment(actor, id); // ownership check
        commentService.add(actor, id, request == null ? null : request.text());
        return commentService.list(id).stream()
                .map(c -> new PaymentDtos.CommentView(c.getAuthor().getFullName(),
                        c.getAuthor().getRole().apiName(), c.getText(), c.getCreatedAt()))
                .toList();
    }
}
