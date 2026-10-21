package com.northbridge.portal.web;

import com.northbridge.portal.domain.InviteToken;
import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.security.PortalUserDetailsService;
import com.northbridge.portal.service.UserLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Login, password reset (single-use, time-bound tokens) and invite acceptance (single-use,
 * time-bound tokens). All forms are CSRF-protected; output is rendered with Thymeleaf's default
 * contextual escaping.
 */
@Controller
public class AuthController {

    private final UserLifecycleService lifecycle;
    private final PortalUserDetailsService userDetailsService;

    public AuthController(UserLifecycleService lifecycle, PortalUserDetailsService userDetailsService) {
        this.lifecycle = lifecycle;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPassword() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(@RequestParam String email, RedirectAttributes redirect) {
        try {
            lifecycle.requestPasswordReset(email);
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/forgot-password";
        }
        redirect.addFlashAttribute("success", "A single-use reset link is on its way.");
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password/{token}")
    public String resetForm(@PathVariable String token, Model model) {
        try {
            lifecycle.validateResetToken(token);
        } catch (RuntimeException e) {
            model.addAttribute("invalid", e.getMessage());
            return "auth/reset-password";
        }
        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password/{token}")
    public String reset(@PathVariable String token, @RequestParam String password,
                        @RequestParam String confirm, Model model, RedirectAttributes redirect) {
        if (!password.equals(confirm)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match");
            return "auth/reset-password";
        }
        try {
            lifecycle.resetPassword(token, password);
        } catch (RuntimeException e) {
            model.addAttribute("token", token);
            model.addAttribute("error", e.getMessage());
            return "auth/reset-password";
        }
        redirect.addFlashAttribute("success", "Password reset. You can sign in now.");
        return "redirect:/login";
    }

    @GetMapping("/invite/{token}")
    public String inviteForm(@PathVariable String token,
                             @RequestParam(required = false) String continueTo, Model model) {
        model.addAttribute("continueTo", continueTo);
        try {
            InviteToken invite = lifecycle.validateInvite(token);
            model.addAttribute("token", token);
            model.addAttribute("invite", invite);
        } catch (RuntimeException e) {
            model.addAttribute("invalid", e.getMessage());
        }
        return "auth/invite";
    }

    @PostMapping("/invite/{token}")
    public String acceptInvite(@PathVariable String token,
                               @RequestParam @NotBlank String fullName,
                               @RequestParam String password,
                               @RequestParam String confirm,
                               @RequestParam(required = false) String continueTo,
                               HttpServletRequest request,
                               Model model, RedirectAttributes redirect) {
        if (!password.equals(confirm)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match");
            return "auth/invite";
        }
        PortalUser user;
        try {
            user = lifecycle.acceptInvite(token, password, fullName);
        } catch (RuntimeException e) {
            model.addAttribute("token", token);
            model.addAttribute("error", e.getMessage());
            return "auth/invite";
        }
        startSession(request, user);
        redirect.addFlashAttribute("success", "Welcome aboard — your account is ready.");
        return "redirect:" + landingFor(continueTo);
    }

    /** Where the newly onboarded user carries on, honouring the destination the invite carried. */
    private String landingFor(String continueTo) {
        return continueTo == null || continueTo.isBlank() ? "/" : continueTo;
    }

    /** Signs the freshly activated account in so onboarding continues without a second form. */
    private void startSession(HttpServletRequest request, PortalUser user) {
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
