package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.security.AuthContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The checker's approval queue: payments awaiting this checker's decision, with the applicable
 * server-side limit and remaining company headroom shown from the decision tier.
 */
@Controller
@RequestMapping("/approvals")
public class ApprovalController {

    private final AuthContext auth;
    private final PaymentApiClient api;

    public ApprovalController(AuthContext auth, PaymentApiClient api) {
        this.auth = auth;
        this.api = api;
    }

    @GetMapping
    @PreAuthorize("hasRole('company_checker')")
    public String queue(@RequestParam(required = false) String status, Model model) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", status == null || status.isBlank()
                ? "PENDING_APPROVAL,PARTIALLY_APPROVED" : status);
        params.put("size", 50);
        Api.PageOfPayments result = api.searchPayments(auth.token(), params);
        List<Api.PaymentSummary> awaiting = result.content().stream()
                .filter(Api.PaymentSummary::awaitingMyApproval)
                .toList();
        Api.Dashboard dashboard = api.dashboard(auth.token());
        model.addAttribute("awaiting", awaiting);
        model.addAttribute("utilisation", dashboard.utilisation());
        model.addAttribute("myLimit", auth.currentUser().getApprovalLimit());
        model.addAttribute("pageTitle", "Approval queue");
        return "approvals/queue";
    }
}
