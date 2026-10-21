package com.northbridge.paymentapi.web.internal;

import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.repo.PaymentRepository;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.*;
import com.northbridge.paymentapi.web.dto.CommonDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoints for the batch support module and the portal's API-key proxy.
 * Authenticated with the shared internal service credential only; system-actor scope.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final BulkFileService bulkFileService;
    private final ReleaseService releaseService;
    private final ReversalService reversalService;
    private final ReconciliationService reconciliationService;
    private final WebhookService webhookService;
    private final UserAdminService userAdminService;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    public InternalController(BulkFileService bulkFileService,
                              ReleaseService releaseService,
                              ReversalService reversalService,
                              ReconciliationService reconciliationService,
                              WebhookService webhookService,
                              UserAdminService userAdminService,
                              PaymentRepository paymentRepository,
                              AuditService auditService) {
        this.bulkFileService = bulkFileService;
        this.releaseService = releaseService;
        this.reversalService = reversalService;
        this.reconciliationService = reconciliationService;
        this.webhookService = webhookService;
        this.userAdminService = userAdminService;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    @PostMapping("/bulk-files/process-due")
    public Map<String, Object> processBulkFiles() {
        AuthSupport.requireService();
        return Map.of("processed", bulkFileService.processDue());
    }

    @PostMapping("/releases/process-due")
    public Map<String, Object> processReleases() {
        AuthSupport.requireService();
        int released = releaseService.processDue();
        int reversalsPosted = reversalService.postDue();
        return Map.of("released", released, "reversalsPosted", reversalsPosted);
    }

    @PostMapping("/reversals/process-due")
    public Map<String, Object> processReversals() {
        AuthSupport.requireService();
        return Map.of("posted", reversalService.postDue());
    }

    @GetMapping("/reconciliation/candidates")
    public List<ReconciliationService.ReleaseCandidate> reconciliationCandidates() {
        AuthSupport.requireService();
        return reconciliationService.reconciliationCandidates();
    }

    @PostMapping("/reconciliation/report")
    public Map<String, Object> reconciliationReport(@RequestBody Requests.ReconciliationReportRequest request) {
        AuthSupport.requireService();
        List<ReconciliationService.ExceptionReport> reports = request.exceptions().stream()
                .map(e -> new ReconciliationService.ExceptionReport(e.paymentId(), e.coreRef(), e.reason()))
                .toList();
        return Map.of("flagged", reconciliationService.reportExceptions(reports));
    }

    @GetMapping("/webhooks/pending")
    public List<CommonDtos.WebhookDeliveryView> pendingWebhooks() {
        AuthSupport.requireService();
        return webhookService.pendingDeliveries().stream()
                .map(d -> new CommonDtos.WebhookDeliveryView(d.getId(), d.getPayment().getId(),
                        d.getPayment().getRef(), d.getUrl(), d.getStatus(), d.getAttempts(),
                        d.getResponseCode(), d.getCreatedAt()))
                .toList();
    }

    @PostMapping("/webhooks/{id}/delivered")
    public Map<String, Object> webhookDelivered(@PathVariable UUID id,
                                                @RequestBody(required = false) Requests.DeliveryOutcomeRequest request) {
        AuthSupport.requireService();
        webhookService.recordDelivery(id,
                request == null ? "MOCKED_DELIVERED" : request.status(),
                request == null ? "200" : request.responseCode());
        return Map.of("status", "recorded");
    }

    /**
     * Company-scoped payment lookup used by the portal's API-key JSON endpoint. The companyId here
     * is supplied by the portal's own API-key record (portal-side metadata), never by the external
     * caller — and the response is always filtered to that company.
     */
    @PostMapping("/payments/lookup")
    public CommonDtos.StatusLookup lookup(@RequestBody Requests.PaymentLookupRequest request) {
        AuthSupport.requireService();
        Payment payment = paymentRepository.findByRefAndCompanyId(request.paymentRef(), request.companyId())
                .orElseThrow(() -> new com.northbridge.paymentapi.security.ApiException(
                        com.northbridge.paymentapi.security.ApiException.Code.NOT_FOUND, "Payment not found"));
        auditService.auditSystemForCompany("portal", request.companyId(), "API_KEY_PAYMENT_LOOKUP",
                "payment", payment.getId(), payment.getRef(),
                "Privileged read via API key proxy",
                null, Map.of("count", 1, "channel", "api_key"),
                "system=portal basis=privileged_read");
        return new CommonDtos.StatusLookup(payment.getRef(), payment.getStatus().name(),
                payment.getAmount(), payment.getCurrency(), payment.getFee(),
                payment.getSettlementAmount(), payment.getValueDate(),
                payment.getDebitAccount().getAccountNumber(),
                payment.getBeneficiary().getAccountNumber(), payment.getUpdatedAt());
    }

    @PostMapping("/users/activate")
    public Map<String, Object> activateUser(@RequestBody Requests.ActivateUserRequest request) {
        AuthSupport.requireService();
        userAdminService.activate(request.userId());
        return Map.of("status", "activated");
    }
}
