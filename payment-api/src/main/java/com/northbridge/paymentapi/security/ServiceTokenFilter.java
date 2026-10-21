package com.northbridge.paymentapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Authenticates INTERNAL service callers on /internal/** (the batch module and the portal's
 * API-key proxy). The caller presents the shared service credential as a bearer token, compared
 * in constant time against the configured value. The resulting principal is a system actor and
 * carries NO company scope — internal endpoints derive scope only from their own database records.
 */
@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final byte[] expectedToken;

    public ServiceTokenFilter(@Value("${app.security.internal-service-token}") String internalServiceToken) {
        if (internalServiceToken == null || internalServiceToken.isBlank() || internalServiceToken.length() < 16) {
            throw new IllegalStateException("Missing or too-short INTERNAL_SERVICE_TOKEN. "
                    + "This tier refuses to start without it (see .env.example / README).");
        }
        this.expectedToken = internalServiceToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean valid = false;
        if (header != null && header.startsWith("Bearer ")) {
            byte[] provided = header.substring(7).getBytes(StandardCharsets.UTF_8);
            valid = MessageDigest.isEqual(provided, expectedToken);
        }
        if (!valid) {
            chain.doFilter(request, response);
            return;
        }

        AuthPrincipal principal = new AuthPrincipal(
                null, "system", "Internal service", null, null, BigDecimal.ZERO, Set.of(), true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null,
                        java.util.List.of(() -> "ROLE_INTERNAL_SERVICE"));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
