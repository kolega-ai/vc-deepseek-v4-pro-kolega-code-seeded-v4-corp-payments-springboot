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
import java.util.*;

/**
 * Company user administration (company_admin): role assignment, approval limits and account
 * assignment. All constraints (limit ≤ company limit, no self role change) are enforced by the
 * decision tier.
 */
@Controller
@RequestMapping("/company/users")
public class CompanyUserController {

    private final AuthContext auth;
    private final PaymentApiClient api;
    private final UserLifecycleService lifecycle;
    private final com.northbridge.portal.repo.PortalUserRepository portalUsers;

    public CompanyUserController(AuthContext auth, PaymentApiClient api,
                                 UserLifecycleService lifecycle,
                                 com.northbridge.portal.repo.PortalUserRepository portalUsers) {
        this.auth = auth;
        this.api = api;
        this.lifecycle = lifecycle;
        this.portalUsers = portalUsers;
    }

    @GetMapping
    @PreAuthorize("hasRole('company_admin')")
    public String list(Model model) {
        model.addAttribute("users", api.companyUsers(auth.token()));
        model.addAttribute("pageTitle", "Company users");
        return "company/users";
    }

    @GetMapping("/invite")
    @PreAuthorize("hasRole('company_admin')")
    public String inviteForm(Model model) {
        model.addAttribute("accounts", api.accounts(auth.token()));
        model.addAttribute("companyLimit", companyLimit());
        model.addAttribute("pageTitle", "Invite user");
        return "company/invite";
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('company_admin')")
    public String invite(@RequestParam String email, @RequestParam String fullName,
                         @RequestParam String role, @RequestParam(required = false) BigDecimal approvalLimit,
                         @RequestParam(required = false) List<UUID> accountIds,
                         RedirectAttributes redirect) {
        try {
            Api.CompanyUser invited = api.inviteUser(auth.token(), Map.of(
                    "email", email, "fullName", fullName, "role", role,
                    "approvalLimit", approvalLimit == null ? BigDecimal.ZERO : approvalLimit,
                    "accountIds", accountIds == null ? List.of() : accountIds));
            lifecycle.recordInvite(invited, auth.currentUser().getCompanyName());
            redirect.addFlashAttribute("success", "Invite sent to " + email + " (" + role + ").");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/company/users";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('company_admin')")
    public String update(@PathVariable UUID id, @RequestParam String fullName,
                         @RequestParam String role, @RequestParam(required = false) BigDecimal approvalLimit,
                         @RequestParam(required = false) List<UUID> accountIds,
                         RedirectAttributes redirect) {
        try {
            Api.CompanyUser updated = api.updateUser(auth.token(), id, Map.of(
                    "fullName", fullName, "role", role,
                    "approvalLimit", approvalLimit == null ? BigDecimal.ZERO : approvalLimit,
                    "accountIds", accountIds == null ? List.of() : accountIds));
            // Mirror the role/limit on the portal user record so navigation reflects it.
            portalUsers.findById(updated.id()).ifPresent(portalUser -> {
                portalUser.setRole(updated.role());
                portalUser.setApprovalLimit(updated.approvalLimit());
                portalUser.setFullName(updated.fullName());
                portalUser.setUpdatedAt(java.time.Instant.now());
                portalUsers.save(portalUser);
            });
            redirect.addFlashAttribute("success", "User " + updated.email() + " updated.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/company/users";
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('company_admin')")
    public String disable(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            api.userStatus(auth.token(), id, "disable");
            redirect.addFlashAttribute("success", "User disabled. Their sessions stop working immediately.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/company/users";
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('company_admin')")
    public String enable(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            api.userStatus(auth.token(), id, "enable");
            redirect.addFlashAttribute("success", "User enabled.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/company/users";
    }

    private BigDecimal companyLimit() {
        try {
            Api.UserProfile me = api.me(auth.token());
            return me.companyDailyLimit();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
