package com.northbridge.portal.security;

import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.repo.PortalUserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/** Access to the authenticated portal user and the signed session token for payment-api calls. */
@Component
public class AuthContext {

    private final PortalUserRepository userRepository;
    private final PaymentApiClient paymentApiClient;

    public AuthContext(PortalUserRepository userRepository, PaymentApiClient paymentApiClient) {
        this.userRepository = userRepository;
        this.paymentApiClient = paymentApiClient;
    }

    public PortalUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PortalUserDetails details) {
            return details.getUser();
        }
        return null;
    }

    public PortalUser requireUser() {
        PortalUser user = currentUser();
        if (user == null) {
            throw new IllegalStateException("Not authenticated");
        }
        return user;
    }

    public UUID currentUserId() {
        PortalUser user = currentUser();
        return user == null ? null : user.getId();
    }

    /** Name of the cookie that remembers which company the workspace is currently scoped to. */
    public static final String COMPANY_COOKIE = "nb_company";

    public UUID currentCompanyId() {
        UUID scoped = scopedCompany();
        if (scoped != null) {
            return scoped;
        }
        PortalUser user = currentUser();
        return user == null ? null : user.getCompanyId();
    }

    /** Reads the workspace company from the cookie a multi-entity user last selected. */
    private UUID scopedCompany() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Cookie[] cookies = attributes.getRequest().getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COMPANY_COOKIE.equals(cookie.getName())) {
                try {
                    return UUID.fromString(cookie.getValue());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public boolean hasRole(String role) {
        PortalUser user = currentUser();
        return user != null && role.equals(user.getRole());
    }

    public boolean isBankUser() {
        PortalUser user = currentUser();
        return user != null && ("bank_ops".equals(user.getRole()) || "bank_admin".equals(user.getRole()));
    }

    /** Mints the opaque signed session token for a payment-api call. */
    public String token() {
        PortalUser user = requireUser();
        String sessionId = currentSessionId();
        return paymentApiClient.sessionToken(user.getId(), sessionId);
    }

    public String currentSessionId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "none";
        }
        HttpServletRequest request = attributes.getRequest();
        HttpSession session = request.getSession(false);
        return session == null ? "none" : session.getId();
    }

    public String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        return attributes.getRequest().getRemoteAddr();
    }
}
