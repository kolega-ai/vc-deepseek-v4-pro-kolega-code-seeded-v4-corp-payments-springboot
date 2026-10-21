package com.northbridge.paymentapi.security;

import com.northbridge.paymentapi.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Helpers to obtain and validate the acting principal resolved from this tier's own database. */
public final class AuthSupport {

    private AuthSupport() {
    }

    public static Optional<AuthPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** The acting principal, or 401. */
    public static AuthPrincipal require() {
        return current().orElseThrow(() -> new ApiException(ApiException.Code.UNAUTHORIZED, "Authentication required"));
    }

    /** The acting principal, which must be a user (never a service actor) with the given role. */
    public static AuthPrincipal requireRole(Role role) {
        AuthPrincipal principal = require();
        if (principal.service() || principal.role() != role) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "This action requires the " + role.apiName() + " role");
        }
        return principal;
    }

    /** The acting principal with one of the given roles. */
    public static AuthPrincipal requireAnyRole(Role... roles) {
        AuthPrincipal principal = require();
        for (Role role : roles) {
            if (!principal.service() && principal.role() == role) {
                return principal;
            }
        }
        throw new ApiException(ApiException.Code.FORBIDDEN, "Insufficient role for this action");
    }

    public static void requireService() {
        AuthPrincipal principal = require();
        if (!principal.service()) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Internal endpoint");
        }
    }
}
