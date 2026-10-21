package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.client.PaymentApiException;
import com.northbridge.portal.security.AuthContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Bank ops release monitor: retry failures, set investigation status/notes, initiate reversals,
 * and view released payments across all companies. bank_ops can never create or approve payments.
 */
@Controller
@RequestMapping("/ops")
public class OpsController {

    private final AuthContext auth;
    private final PaymentApiClient api;

    public OpsController(AuthContext auth, PaymentApiClient api) {
        this.auth = auth;
        this.api = api;
    }

    @GetMapping("/releases")
    @PreAuthorize("hasAnyRole('bank_ops','bank_admin')")
    public String releases(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String investigation,
                           Model model) {
        model.addAttribute("releases", api.releases(auth.token(), status, investigation));
        model.addAttribute("status", status);
        model.addAttribute("investigation", investigation);
        model.addAttribute("pageTitle", "Release monitor");
        return "ops/releases";
    }

    @PostMapping("/releases/{paymentId}/retry")
    @PreAuthorize("hasRole('bank_ops')")
    public String retry(@PathVariable UUID paymentId, RedirectAttributes redirect) {
        try {
            api.retryRelease(auth.token(), paymentId);
            redirect.addFlashAttribute("success", "Release retried. Check the status below.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ops/releases";
    }

    @PostMapping("/releases/{paymentId}/attempt/discard")
    @PreAuthorize("hasRole('bank_ops')")
    public String discardAttempt(@PathVariable UUID paymentId, RedirectAttributes redirect) {
        try {
            api.discardReleaseAttempt(auth.token(), paymentId);
            redirect.addFlashAttribute("success", "Release attempt record discarded.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ops/releases";
    }

    @PostMapping("/releases/{paymentId}/investigation")
    @PreAuthorize("hasRole('bank_ops')")
    public String investigate(@PathVariable UUID paymentId,
                              @RequestParam String investigationStatus,
                              @RequestParam(required = false) String notes,
                              RedirectAttributes redirect) {
        try {
            api.investigate(auth.token(), paymentId, investigationStatus, notes);
            redirect.addFlashAttribute("success", "Investigation status updated.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ops/releases";
    }

    @PostMapping("/payments/{paymentId}/reversals")
    @PreAuthorize("hasRole('bank_ops')")
    public String requestReversal(@PathVariable UUID paymentId, @RequestParam String reason,
                                  RedirectAttributes redirect) {
        try {
            Api.Reversal reversal = api.requestReversal(auth.token(), paymentId, reason);
            redirect.addFlashAttribute("success",
                    "Reversal requested for " + reversal.paymentRef() + ". A bank administrator must approve it.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ops/releases";
    }

    @GetMapping("/reversals")
    @PreAuthorize("hasAnyRole('bank_ops','bank_admin')")
    public String reversals(Model model) {
        model.addAttribute("reversals", api.reversals(auth.token(), null));
        model.addAttribute("pageTitle", "Reversals");
        return "ops/reversals";
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize("hasAnyRole('bank_ops','bank_admin')")
    public String payment(@PathVariable UUID id, Model model) {
        Api.PaymentDetail detail = api.bankPayment(auth.token(), id);
        model.addAttribute("payment", detail);
        model.addAttribute("pageTitle", "Payment " + detail.summary().ref());
        return "payments/detail";
    }
}
