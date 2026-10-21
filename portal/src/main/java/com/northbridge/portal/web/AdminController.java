package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.client.PaymentApiException;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.service.UserLifecycleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank administration: company onboarding, company-level limits, account provisioning, company
 * admin invites, reversal decisions (a bank_admin right distinct from customer-payment approval)
 * and the global audit log. bank_admin can never approve a customer payment.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AuthContext auth;
    private final PaymentApiClient api;
    private final UserLifecycleService lifecycle;

    public AdminController(AuthContext auth, PaymentApiClient api, UserLifecycleService lifecycle) {
        this.auth = auth;
        this.api = api;
        this.lifecycle = lifecycle;
    }

    // ------------------------------------------------------------------ companies

    @GetMapping("/companies")
    @PreAuthorize("hasRole('bank_admin')")
    public String companies(Model model) {
        model.addAttribute("companies", api.companies(auth.token()));
        model.addAttribute("pageTitle", "Companies");
        return "admin/companies";
    }

    @GetMapping("/companies/new")
    @PreAuthorize("hasRole('bank_admin')")
    public String companyForm(Model model) {
        model.addAttribute("pageTitle", "Onboard company");
        return "admin/company-form";
    }

    @PostMapping("/companies")
    @PreAuthorize("hasRole('bank_admin')")
    public String createCompany(@RequestParam String legalName,
                                @RequestParam String registrationNumber,
                                @RequestParam String baseCurrency,
                                @RequestParam BigDecimal dailyLimit,
                                RedirectAttributes redirect) {
        try {
            Api.CompanyView company = api.createCompany(auth.token(), Map.of(
                    "legalName", legalName, "registrationNumber", registrationNumber,
                    "baseCurrency", baseCurrency, "dailyLimit", dailyLimit));
            redirect.addFlashAttribute("success",
                    "Company " + company.legalName() + " onboarded. Invite its first administrator below.");
            return "redirect:/admin/companies/" + company.id();
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/companies/new";
        }
    }

    @GetMapping("/companies/{id}")
    @PreAuthorize("hasRole('bank_admin')")
    public String company(@PathVariable UUID id, Model model) {
        Api.CompanyView company = api.company(auth.token(), id);
        model.addAttribute("company", company);
        model.addAttribute("accounts", api.bankAccounts(auth.token(), id));
        model.addAttribute("pageTitle", company.legalName());
        return "admin/company";
    }

    @PostMapping("/companies/{id}/limit")
    @PreAuthorize("hasRole('bank_admin')")
    public String setLimit(@PathVariable UUID id, @RequestParam BigDecimal dailyLimit,
                           @RequestParam String reason, RedirectAttributes redirect) {
        try {
            api.setCompanyLimit(auth.token(), id, Map.of("dailyLimit", dailyLimit, "reason", reason));
            redirect.addFlashAttribute("success", "Daily limit updated to " + dailyLimit + ".");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/companies/" + id;
    }

    @PostMapping("/companies/{id}/accounts")
    @PreAuthorize("hasRole('bank_admin')")
    public String createAccount(@PathVariable UUID id, @RequestParam String accountNumber,
                                @RequestParam String currency, @RequestParam String product,
                                @RequestParam BigDecimal openingBalance, RedirectAttributes redirect) {
        try {
            api.createAccount(auth.token(), Map.of("companyId", id, "accountNumber", accountNumber,
                    "currency", currency, "product", product, "openingBalance", openingBalance));
            redirect.addFlashAttribute("success", "Account " + accountNumber + " provisioned in the core system.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/companies/" + id;
    }

    @PostMapping("/companies/{id}/admins/invite")
    @PreAuthorize("hasRole('bank_admin')")
    public String inviteAdmin(@PathVariable UUID id, @RequestParam String email,
                              @RequestParam String fullName, RedirectAttributes redirect) {
        try {
            Api.CompanyUser invited = api.inviteCompanyAdmin(auth.token(), id,
                    Map.of("email", email, "fullName", fullName));
            String companyName = api.company(auth.token(), id).legalName();
            lifecycle.recordBankInvite(id, invited, companyName);
            redirect.addFlashAttribute("success",
                    "Company-admin invite sent to " + email + " for " + companyName + ".");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/companies/" + id;
    }

    @PostMapping("/companies/{id}/directory/verify")
    @PreAuthorize("hasRole('bank_admin')")
    public String verifyOfficer(@PathVariable UUID id, @RequestParam String officerId,
                                RedirectAttributes flash) {
        try {
            Map<String, Object> result = api.verifyStaff(auth.token(), officerId);
            boolean found = Boolean.TRUE.equals(result.get("found"));
            flash.addFlashAttribute(found ? "success" : "error",
                    found ? "Directory confirms " + result.get("name") + "."
                            : "No staff directory entry for " + officerId + ".");
        } catch (PaymentApiException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/companies/" + id;
    }

    @GetMapping("/companies/{id}/directory/search")
    @PreAuthorize("hasRole('bank_admin')")
    public String searchDirectory(@PathVariable UUID id, @RequestParam String query,
                                  RedirectAttributes flash) {
        try {
            List<Map<String, Object>> matches = api.searchStaff(auth.token(), query);
            flash.addFlashAttribute(matches.isEmpty() ? "error" : "success",
                    matches.isEmpty() ? "No staff directory entries matched."
                            : "Directory matched " + matches.size() + " staff entry(ies).");
        } catch (PaymentApiException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/companies/" + id;
    }

    // ------------------------------------------------------------------ reversals

    @GetMapping("/reversals")
    @PreAuthorize("hasRole('bank_admin')")
    public String reversals(Model model) {
        model.addAttribute("reversals", api.reversals(auth.token(), null));
        model.addAttribute("pageTitle", "Reversal approvals");
        return "admin/reversals";
    }

    @PostMapping("/reversals/{id}/approve")
    @PreAuthorize("hasRole('bank_admin')")
    public String approveReversal(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            Api.Reversal reversal = api.decideReversal(auth.token(), id, true);
            redirect.addFlashAttribute("success",
                    "Reversal for " + reversal.paymentRef() + " approved. It will post on the next batch cycle.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/reversals";
    }

    @PostMapping("/reversals/{id}/decline")
    @PreAuthorize("hasRole('bank_admin')")
    public String declineReversal(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            Api.Reversal reversal = api.decideReversal(auth.token(), id, false);
            redirect.addFlashAttribute("success", "Reversal for " + reversal.paymentRef() + " declined.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/reversals";
    }

    // ------------------------------------------------------------------ audit

    @GetMapping("/audit")
    @PreAuthorize("hasRole('bank_admin')")
    public String audit(@RequestParam(required = false) String action,
                        @RequestParam(required = false) String resourceType,
                        @RequestParam(required = false) UUID companyId,
                        @RequestParam(required = false) Instant from,
                        @RequestParam(required = false) Instant to,
                        @RequestParam(required = false, defaultValue = "0") int page,
                        Model model) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("action", action);
        params.put("resourceType", resourceType);
        params.put("companyId", companyId);
        params.put("from", from == null ? null : from.toString());
        params.put("to", to == null ? null : to.toString());
        params.put("page", page);
        params.put("size", 25);
        Api.PageOfAudit result = api.auditPage(auth.token(), params);
        model.addAttribute("auditPage", result);
        model.addAttribute("filters", params);
        model.addAttribute("companies", api.companies(auth.token()));
        model.addAttribute("pageTitle", "Audit log");
        return "admin/audit";
    }
}
