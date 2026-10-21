package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.security.ApiException;
import com.northbridge.portal.service.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Per-company API key management plus the JSON payment-status endpoint authenticated by API key.
 * The key is shown exactly once at creation; only its SHA-256 hash is stored.
 */
@Controller
@RequestMapping("/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    @PreAuthorize("hasRole('company_admin')")
    public String list(Model model) {
        model.addAttribute("keys", apiKeyService.listForCompany());
        model.addAttribute("pageTitle", "API keys");
        return "api-keys/list";
    }

    @PostMapping
    @PreAuthorize("hasRole('company_admin')")
    public String create(@RequestParam String name, Model model, RedirectAttributes redirect) {
        try {
            ApiKeyService.CreatedKey created = apiKeyService.create(name);
            redirect.addFlashAttribute("newKey", created.clearKey());
            redirect.addFlashAttribute("newKeyName", created.apiKey().getName());
        } catch (ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/api-keys";
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('company_admin')")
    public String revoke(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            apiKeyService.revoke(id);
            redirect.addFlashAttribute("success", "API key revoked.");
        } catch (ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/api-keys";
    }
}
