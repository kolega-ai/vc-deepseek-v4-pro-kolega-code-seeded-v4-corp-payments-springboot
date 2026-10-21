package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.service.UserLifecycleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Notifications (from the decision tier) and the profile/password screens. */
@Controller
public class ProfileController {

    private final AuthContext auth;
    private final PaymentApiClient api;
    private final UserLifecycleService lifecycle;

    public ProfileController(AuthContext auth, PaymentApiClient api, UserLifecycleService lifecycle) {
        this.auth = auth;
        this.api = api;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/notifications")
    public String notifications(@RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
                                Model model) {
        model.addAttribute("notifications", api.notifications(auth.token(), unreadOnly));
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("pageTitle", "Notifications");
        return "notifications";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@PathVariable UUID id, RedirectAttributes redirect) {
        api.markRead(auth.token(), id);
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(RedirectAttributes redirect) {
        api.markAllRead(auth.token());
        redirect.addFlashAttribute("success", "All notifications marked as read.");
        return "redirect:/notifications";
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        Api.UserProfile me = api.me(auth.token());
        model.addAttribute("me", me);
        model.addAttribute("pageTitle", "Your profile");
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute ProfileDetailsForm form, RedirectAttributes redirect) {
        try {
            lifecycle.updateProfile(form);
            redirect.addFlashAttribute("success", "Profile updated.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirm,
                                 RedirectAttributes redirect) {
        if (!newPassword.equals(confirm)) {
            redirect.addFlashAttribute("error", "New passwords do not match.");
            return "redirect:/profile";
        }
        try {
            lifecycle.changePassword(currentPassword, newPassword);
            redirect.addFlashAttribute("success", "Password changed.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/signing-phrase")
    public String setSigningPhrase(@RequestParam String phrase, RedirectAttributes redirect) {
        try {
            lifecycle.setSigningPhrase(phrase);
            redirect.addFlashAttribute("success", "Signing phrase saved.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/signing-phrase/verify")
    public String verifySigningPhrase(@RequestParam String phrase, RedirectAttributes redirect) {
        boolean matched = lifecycle.matchesSigningPhrase(phrase);
        redirect.addFlashAttribute(matched ? "success" : "error",
                matched ? "Signing phrase confirmed." : "Signing phrase did not match.");
        return "redirect:/profile";
    }
}
