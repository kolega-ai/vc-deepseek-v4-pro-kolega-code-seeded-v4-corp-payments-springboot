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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Beneficiary management (company_admin): add, amend, activate, suspend. New beneficiaries and
 * account-number amendments enter a 30-minute cooling period enforced by the decision tier.
 */
@Controller
@RequestMapping("/beneficiaries")
public class BeneficiaryController {

    private final AuthContext auth;
    private final PaymentApiClient api;

    public BeneficiaryController(AuthContext auth, PaymentApiClient api) {
        this.auth = auth;
        this.api = api;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String list(Model model) {
        model.addAttribute("beneficiaries", api.beneficiaries(auth.token()));
        model.addAttribute("pageTitle", "Beneficiaries");
        return "beneficiaries/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('company_admin')")
    public String createForm(Model model) {
        model.addAttribute("beneficiary", new Api.Beneficiary(null, "", "", "", "GBP",
                "PENDING_COOLING", null, false, null, null, null));
        model.addAttribute("pageTitle", "Add beneficiary");
        return "beneficiaries/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('company_admin')")
    public String create(@RequestParam String name, @RequestParam String accountNumber,
                         @RequestParam String bankCode, @RequestParam String currency,
                         RedirectAttributes redirect) {
        try {
            Api.Beneficiary created = api.createBeneficiary(auth.token(), Map.of(
                    "name", name, "accountNumber", accountNumber,
                    "bankCode", bankCode, "currency", currency));
            redirect.addFlashAttribute("success",
                    "Beneficiary " + created.name() + " added. It enters a 30-minute cooling period before first use.");
            return "redirect:/beneficiaries";
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/beneficiaries/new";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('company_admin')")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes redirect) {
        Api.Beneficiary beneficiary = api.beneficiaries(auth.token()).stream()
                .filter(b -> b.id().equals(id)).findFirst().orElse(null);
        if (beneficiary == null) {
            redirect.addFlashAttribute("error", "Beneficiary not found");
            return "redirect:/beneficiaries";
        }
        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("pageTitle", "Amend beneficiary");
        return "beneficiaries/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('company_admin')")
    public String update(@PathVariable UUID id, @RequestParam String name,
                         @RequestParam String accountNumber, @RequestParam String bankCode,
                         @RequestParam String currency, RedirectAttributes redirect) {
        try {
            api.updateBeneficiary(auth.token(), id, Map.of(
                    "name", name, "accountNumber", accountNumber,
                    "bankCode", bankCode, "currency", currency));
            redirect.addFlashAttribute("success",
                    "Beneficiary updated. Account-number changes restart the cooling period.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/beneficiaries";
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('company_admin')")
    public String suspend(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            api.beneficiaryAction(auth.token(), id, "suspend");
            redirect.addFlashAttribute("success", "Beneficiary suspended.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/beneficiaries";
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('company_admin')")
    public String activate(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            api.beneficiaryAction(auth.token(), id, "activate");
            redirect.addFlashAttribute("success", "Beneficiary reactivated.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/beneficiaries";
    }
}
