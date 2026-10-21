package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.client.PaymentApiException;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.service.EmailOutboxService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Company settings: seeded fee schedule, FX rates and the company webhook configuration. */
@Controller
@RequestMapping("/company/settings")
public class CompanySettingsController {

    private final AuthContext auth;
    private final PaymentApiClient api;
    private final EmailOutboxService emailOutboxService;

    public CompanySettingsController(AuthContext auth, PaymentApiClient api,
                                     EmailOutboxService emailOutboxService) {
        this.auth = auth;
        this.api = api;
        this.emailOutboxService = emailOutboxService;
    }

    @GetMapping
    @PreAuthorize("hasRole('company_admin')")
    public String settings(Model model) {
        Api.Settings settings = api.companySettings(auth.token());
        model.addAttribute("settings", settings);
        model.addAttribute("pageTitle", "Company settings");
        return "company/settings";
    }

    @PostMapping("/webhook")
    @PreAuthorize("hasRole('company_admin')")
    public String saveWebhook(@RequestParam String url,
                              @RequestParam(required = false) String secret,
                              @RequestParam(required = false, defaultValue = "false") boolean active,
                              RedirectAttributes redirect) {
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("url", url);
            body.put("secret", secret == null || secret.isBlank() ? null : secret);
            body.put("active", active);
            api.saveWebhook(auth.token(), body);
            redirect.addFlashAttribute("success", "Webhook configuration saved.");
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/company/settings";
    }

    @PostMapping("/template-preview")
    @PreAuthorize("hasRole('company_admin')")
    public String templatePreview(@RequestParam String template, RedirectAttributes flash) {
        try {
            emailOutboxService.sendTemplatePreview(auth.requireUser(), template);
            flash.addFlashAttribute("success", "Template rendered and queued to your inbox as a preview.");
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "The template could not be rendered.");
        }
        return "redirect:/company/settings";
    }

    @PostMapping("/form-layout")
    @PreAuthorize("hasRole('company_admin')")
    public String saveFormLayout(@RequestParam String layout, RedirectAttributes flash) {
        try {
            Api.FormLayout saved = api.saveFormLayout(auth.token(), layout);
            flash.addFlashAttribute("success",
                    "Payment form layout saved (" + saved.widgets() + " widget(s)).");
        } catch (PaymentApiException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/company/settings";
    }
}
