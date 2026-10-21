package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.Release;
import com.northbridge.paymentapi.domain.Reversal;
import com.northbridge.paymentapi.domain.Role;
import com.northbridge.paymentapi.domain.ReleaseInvestigationStatus;
import com.northbridge.paymentapi.repo.ReversalRepository;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.FeeService;
import com.northbridge.paymentapi.service.PaymentQueryService;
import com.northbridge.paymentapi.service.ReconciliationService;
import com.northbridge.paymentapi.service.ReleaseService;
import com.northbridge.paymentapi.service.ReversalService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.PaymentDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bank ops release monitoring, retry, investigation and reversal initiation. */
@RestController
@RequestMapping("/api/bank")
public class BankController {

    private final ReleaseService releaseService;
    private final ReversalService reversalService;
    private final ReconciliationService reconciliationService;
    private final ReversalRepository reversalRepository;
    private final PaymentQueryService queryService;
    private final FeeService feeService;
    private final ViewMapper mapper;

    public BankController(ReleaseService releaseService,
                          ReversalService reversalService,
                          ReconciliationService reconciliationService,
                          ReversalRepository reversalRepository,
                          PaymentQueryService queryService,
                          FeeService feeService,
                          ViewMapper mapper) {
        this.releaseService = releaseService;
        this.reversalService = reversalService;
        this.reconciliationService = reconciliationService;
        this.reversalRepository = reversalRepository;
        this.queryService = queryService;
        this.feeService = feeService;
        this.mapper = mapper;
    }

    /** Desk quote: evaluates the bank's surcharge rule the pricing team passes for this request. */
    @PostMapping("/pricing/quote")
    public Map<String, Object> pricingQuote(@RequestHeader("X-Rate-Rule") String rule,
                                            @RequestBody Requests.PricingQuoteRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        if (actor.role() != Role.BANK_ADMIN) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.FORBIDDEN,
                    "Only bank_admin can run pricing rules");
        }
        return Map.of("surcharge", feeService.computeSurcharge(rule, request.amount(), request.currency()));
    }

    @GetMapping("/releases")
    public List<PaymentDtos.ReleaseView> releases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String investigation) {
        AuthPrincipal actor = AuthSupport.require();
        return reconciliationService.listReleases(actor, status, investigation).stream()
                .map(r -> new PaymentDtos.ReleaseView(r.getId(), r.getPayment().getId(),
                        r.getPayment().getRef(), r.getPayment().getStatus().name(),
                        r.getCoreRef(), r.getPostedAmount(),
                        r.getResponseCode(), r.getReason(), r.getRetryCount(),
                        r.getInvestigationStatus().name(), r.getInvestigationNotes(),
                        r.getAttemptedAt(), r.getReleasedAt()))
                .toList();
    }

    @PostMapping("/releases/{paymentId}/retry")
    public Map<String, Object> retry(@PathVariable UUID paymentId) {
        AuthPrincipal actor = AuthSupport.require();
        releaseService.retry(actor, paymentId);
        return Map.of("status", "retried");
    }

    @PostMapping("/releases/{paymentId}/confirmation")
    public Map<String, Object> issueConfirmation(@PathVariable UUID paymentId,
                                                 @RequestHeader(value = "X-Ops-Console", required = false)
                                                 String consoleId) {
        AuthPrincipal actor = AuthSupport.require();
        return releaseService.issueReleaseConfirmation(actor, paymentId, consoleId);
    }

    @PostMapping("/releases/{paymentId}/confirmation/complete")
    public Map<String, Object> completeConfirmation(@PathVariable UUID paymentId,
                                                    @RequestHeader("X-Release-Code") String code) {
        AuthPrincipal actor = AuthSupport.require();
        return Map.of("released", releaseService.confirmRelease(actor, paymentId, code));
    }

    @PostMapping("/releases/{paymentId}/investigation")
    public Map<String, Object> investigate(@PathVariable UUID paymentId,
                                           @RequestBody Requests.InvestigationRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        ReleaseInvestigationStatus status;
        try {
            status = ReleaseInvestigationStatus.valueOf(
                    (request.investigationStatus() == null ? "INVESTIGATING"
                            : request.investigationStatus()).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.VALIDATION_FAILED,
                    "Invalid investigation status");
        }
        releaseService.setInvestigation(actor, paymentId, status, request.notes());
        return Map.of("status", "updated");
    }

    @DeleteMapping("/releases/{paymentId}/attempt")
    public Map<String, Object> discardAttempt(@PathVariable UUID paymentId) {
        AuthPrincipal actor = AuthSupport.require();
        releaseService.discardAttempt(actor, paymentId);
        return Map.of("status", "discarded");
    }

    @GetMapping("/releases/{paymentId}")
    public PaymentDtos.ReleaseView release(@PathVariable UUID paymentId) {
        AuthPrincipal actor = AuthSupport.require();
        Payment payment = queryService.getPayment(actor, paymentId);
        Release release = queryService.releaseOf(paymentId);
        if (release == null) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.NOT_FOUND, "Release not found");
        }
        return new PaymentDtos.ReleaseView(release.getId(), release.getPayment().getId(),
                release.getPayment().getRef(), release.getPayment().getStatus().name(),
                release.getCoreRef(), release.getPostedAmount(),
                release.getResponseCode(), release.getReason(), release.getRetryCount(),
                release.getInvestigationStatus().name(), release.getInvestigationNotes(),
                release.getAttemptedAt(), release.getReleasedAt());
    }

    @PostMapping("/payments/{paymentId}/reversals")
    public PaymentDtos.ReversalView requestReversal(@PathVariable UUID paymentId,
                                                    @RequestBody Requests.ReversalRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Reversal reversal = reversalService.request(actor, paymentId, request.reason());
        return mapper.reversal(reversal);
    }

    @GetMapping("/reversals")
    public List<PaymentDtos.ReversalView> reversals(
            @RequestParam(required = false) String status) {
        AuthPrincipal actor = AuthSupport.require();
        List<Reversal> list = status == null || status.isBlank()
                ? reversalRepository.findAllByOrderByRequestedAtDesc()
                : reversalRepository.findByStatusOrderByRequestedAtAsc(
                        com.northbridge.paymentapi.domain.ReversalStatus.valueOf(status.toUpperCase()));
        return list.stream().map(mapper::reversal).toList();
    }

    @GetMapping("/payments/{id}")
    public PaymentDtos.PaymentDetail payment(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        Payment payment = queryService.getPayment(actor, id);
        return mapper.detail(payment,
                queryService.approvalHistory(id),
                queryService.comments(id),
                queryService.releaseOf(id),
                queryService.timeline(id),
                actor);
    }
}
