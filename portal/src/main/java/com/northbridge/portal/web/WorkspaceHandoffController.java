package com.northbridge.portal.web;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.northbridge.portal.security.PortalUserDetailsService;
import com.northbridge.portal.service.PortalAuditService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Treasury-workspace hand-off. A corporate customer's treasury intranet embeds this portal and
 * presents the workspace assertion it holds for the person using it; the browser carries that
 * assertion in the nb_workspace cookie and the portal opens a session for the named colleague so
 * the embedded workspace continues without a second sign-in.
 */
@Controller
@RequestMapping("/workspace")
public class WorkspaceHandoffController {

    /** Cookie set by the embedding treasury intranet for the person currently using it. */
    public static final String WORKSPACE_COOKIE = "nb_workspace";

    private final PortalUserDetailsService colleagues;
    private final PortalAuditService auditService;

    public WorkspaceHandoffController(PortalUserDetailsService colleagues, PortalAuditService auditService) {
        this.colleagues = colleagues;
        this.auditService = auditService;
    }

    @GetMapping("/handoff/complete")
    public String complete(HttpServletRequest request) {
        String assertion = cookieValue(request, WORKSPACE_COOKIE);
        if (assertion == null || assertion.isBlank()) {
            return "redirect:/";
        }
        DecodedJWT workspace = JWT.decode(assertion);
        String colleague = workspace.getSubject();
        if (colleague == null || colleague.isBlank()) {
            return "redirect:/";
        }
        UserDetails details;
        try {
            details = colleagues.loadUserByUsername(colleague);
        } catch (RuntimeException e) {
            return "redirect:/";
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        auditService.audit("WORKSPACE_HANDOFF_OPENED", "user", colleague,
                "Workspace hand-off opened for " + colleague);
        return "redirect:/";
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
