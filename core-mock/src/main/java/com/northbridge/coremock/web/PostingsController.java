package com.northbridge.coremock.web;

import com.northbridge.coremock.security.ScopeAuthInterceptor;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.service.PaymentStatusService;
import com.northbridge.coremock.service.PostingService;
import com.northbridge.coremock.web.dto.PaymentStatusResponse;
import com.northbridge.coremock.web.dto.PostingRequest;
import com.northbridge.coremock.web.dto.PostingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment execution surface:
 * <ul>
 *   <li>POST /v1/postings — execute (or decline) a posting for a payment.</li>
 *   <li>GET /v1/payment-status/{coreRef} — query a payment's status by core reference.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1")
public class PostingsController {

    private final PostingService postingService;
    private final PaymentStatusService paymentStatusService;

    public PostingsController(PostingService postingService, PaymentStatusService paymentStatusService) {
        this.postingService = postingService;
        this.paymentStatusService = paymentStatusService;
    }

    @PostMapping("/postings")
    public PostingResponse post(
            @RequestAttribute(ScopeAuthInterceptor.SCOPE_TOKEN_ATTRIBUTE) ScopeToken scopeToken,
            @Valid @RequestBody PostingRequest request) {
        return postingService.post(scopeToken, request);
    }

    @GetMapping("/payment-status/{coreRef}")
    public PaymentStatusResponse paymentStatus(
            @RequestAttribute(ScopeAuthInterceptor.SCOPE_TOKEN_ATTRIBUTE) ScopeToken scopeToken,
            @PathVariable String coreRef) {
        return paymentStatusService.find(scopeToken, coreRef);
    }
}
