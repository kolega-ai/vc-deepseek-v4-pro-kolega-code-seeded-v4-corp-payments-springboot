package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.client.PaymentApiException;
import com.northbridge.portal.security.AuthContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Payment screens: searchable list, detail with timeline/comments, two-step creation with a
 * server-computed fee/FX preview, and the maker/checker workflow actions. Every action is
 * authorized by the payment-api decision tier; the portal never computes an authorization result.
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final AuthContext auth;
    private final PaymentApiClient api;

    public PaymentController(AuthContext auth, PaymentApiClient api) {
        this.auth = auth;
        this.api = api;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) UUID accountId,
                       @RequestParam(required = false) UUID beneficiaryId,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) BigDecimal minAmount,
                       @RequestParam(required = false) BigDecimal maxAmount,
                       @RequestParam(required = false, defaultValue = "createdAt") String sort,
                       @RequestParam(required = false, defaultValue = "desc") String dir,
                       @RequestParam(required = false, defaultValue = "0") int page,
                       Model model) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", status);
        params.put("from", from == null ? null : from.toString());
        params.put("to", to == null ? null : to.toString());
        params.put("accountId", accountId);
        params.put("beneficiaryId", beneficiaryId);
        params.put("q", q);
        params.put("minAmount", minAmount);
        params.put("maxAmount", maxAmount);
        params.put("sort", sort);
        params.put("dir", dir);
        params.put("page", page);
        params.put("size", 20);
        Api.PageOfPayments result = api.searchPayments(auth.token(), params);
        model.addAttribute("payments", result.content());
        model.addAttribute("pageInfo", result);
        model.addAttribute("filters", params);
        model.addAttribute("accounts", api.accounts(auth.token()));
        model.addAttribute("beneficiaries", api.beneficiaries(auth.token()));
        model.addAttribute("statuses", List.of("DRAFT", "PENDING_APPROVAL", "PARTIALLY_APPROVED",
                "APPROVED", "QUEUED_FOR_RELEASE", "RELEASED", "REJECTED", "RETURNED", "FAILED", "REVERSED"));
        model.addAttribute("pageTitle", "Payments");
        return "payments/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('company_maker')")
    public String createForm(Model model) {
        model.addAttribute("accounts", api.accounts(auth.token()));
        model.addAttribute("beneficiaries", api.beneficiaries(auth.token()));
        model.addAttribute("pageTitle", "New payment");
        return "payments/create";
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('company_maker')")
    public String preview(@RequestParam UUID debitAccountId,
                          @RequestParam UUID beneficiaryId,
                          @RequestParam BigDecimal amount,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valueDate,
                          @RequestParam String reference,
                          Model model, RedirectAttributes redirect) {
        try {
            Api.FeePreview preview = api.preview(auth.token(), Map.of(
                    "debitAccountId", debitAccountId, "beneficiaryId", beneficiaryId, "amount", amount));
            model.addAttribute("preview", preview);
            model.addAttribute("form", Map.of(
                    "debitAccountId", debitAccountId, "beneficiaryId", beneficiaryId,
                    "amount", amount.toPlainString(), "valueDate", valueDate.toString(),
                    "reference", reference));
            model.addAttribute("accounts", api.accounts(auth.token()));
            model.addAttribute("beneficiaries", api.beneficiaries(auth.token()));
            model.addAttribute("pageTitle", "Confirm payment");
            return "payments/confirm";
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/payments/new";
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('company_maker')")
    public String create(@RequestParam UUID debitAccountId,
                         @RequestParam UUID beneficiaryId,
                         @RequestParam BigDecimal amount,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valueDate,
                         @RequestParam String reference,
                         @RequestParam(required = false) String submit,
                         RedirectAttributes redirect) {
        try {
            Api.PaymentSummary payment = api.createPayment(auth.token(), Map.of(
                    "debitAccountId", debitAccountId, "beneficiaryId", beneficiaryId,
                    "amount", amount, "valueDate", valueDate.toString(), "reference", reference));
            if ("true".equals(submit)) {
                api.paymentAction(auth.token(), payment.id(), "submit", Map.of());
                redirect.addFlashAttribute("success",
                        "Payment " + payment.ref() + " created and submitted for approval.");
            } else {
                redirect.addFlashAttribute("success", "Payment " + payment.ref() + " saved as draft.");
            }
            return "redirect:/payments/" + payment.id();
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/payments/new";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public String detail(@PathVariable UUID id, Model model) {
        Api.PaymentDetail detail = api.payment(auth.token(), id);
        model.addAttribute("payment", detail);
        model.addAttribute("pageTitle", "Payment " + detail.summary().ref());
        return "payments/detail";
    }

    @GetMapping(value = "/{id}/receipt", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public ResponseEntity<String> receipt(@PathVariable UUID id,
                                          @RequestParam(required = false) String note) {
        Api.PaymentDetail detail = api.payment(auth.token(), id);
        String html = "<html><body><h1>Payment receipt</h1>"
                + "<p>Reference: " + detail.summary().ref() + "</p>"
                + "<p>Beneficiary: " + detail.summary().beneficiaryName() + "</p>"
                + "<p>Amount: " + detail.summary().amount() + " " + detail.summary().currency() + "</p>"
                + (note == null ? "" : "<p>Note: " + note + "</p>")
                + "</body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('company_maker')")
    public String submit(@PathVariable UUID id, RedirectAttributes redirect) {
        return action(id, "submit", Map.of(), redirect);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('company_checker')")
    public String approve(@PathVariable UUID id, @RequestParam(required = false) String comment,
                          RedirectAttributes redirect) {
        return action(id, "approve", Map.of("comment", comment == null ? "" : comment), redirect);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('company_checker')")
    public String reject(@PathVariable UUID id, @RequestParam(required = false) String comment,
                         RedirectAttributes redirect) {
        return action(id, "reject", Map.of("comment", comment == null ? "" : comment), redirect);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('company_checker')")
    public String returnForAmendment(@PathVariable UUID id, @RequestParam(required = false) String comment,
                                     RedirectAttributes redirect) {
        return action(id, "return", Map.of("comment", comment == null ? "" : comment), redirect);
    }

    @PostMapping("/{id}/amend")
    @PreAuthorize("hasRole('company_maker')")
    public String amend(@PathVariable UUID id,
                        @RequestParam(required = false) BigDecimal amount,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valueDate,
                        @RequestParam(required = false) String reference,
                        @RequestParam(required = false) UUID beneficiaryId,
                        RedirectAttributes redirect) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("valueDate", valueDate == null ? null : valueDate.toString());
        body.put("reference", reference);
        body.put("beneficiaryId", beneficiaryId);
        return action(id, "amend", body, redirect);
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String comment(@PathVariable UUID id, @RequestParam String text, RedirectAttributes redirect) {
        try {
            api.addComment(auth.token(), id, text);
            redirect.addFlashAttribute("success", "Comment added.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/payments/" + id;
    }

    private String action(UUID id, String action, Map<String, Object> body, RedirectAttributes redirect) {
        try {
            Api.PaymentSummary payment = api.paymentAction(auth.token(), id, action, body);
            String message = switch (action) {
                case "submit" -> "Payment " + payment.ref() + " submitted for approval.";
                case "approve" -> payment.status().equals("PARTIALLY_APPROVED")
                        ? "First approval recorded for " + payment.ref() + " — a second checker must approve."
                        : "Payment " + payment.ref() + " approved.";
                case "reject" -> "Payment " + payment.ref() + " rejected.";
                case "return" -> "Payment " + payment.ref() + " returned for amendment.";
                case "amend" -> "Payment " + payment.ref() + " amended back to draft.";
                default -> "Done.";
            };
            redirect.addFlashAttribute("success", message);
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/payments/" + id;
    }
}
