package com.northbridge.paymentapi.security;

import com.northbridge.paymentapi.domain.CompanyUser;
import com.northbridge.paymentapi.domain.Role;
import com.northbridge.paymentapi.domain.UserStatus;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticates portal (channel-tier) session tokens on /api/**.
 *
 * <p>The token is opaque and signed by the portal. This tier verifies the signature, extracts the
 * subject, and then loads the acting user, role, company scope and approval limit FROM ITS OWN
 * DATABASE on every request. Any user id, role, company or limit carried in a header, query, form
 * field or body is IGNORED — it is never read for authorization.</p>
 */
@Component
public class SessionTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenFilter.class);

    private final TokenCodec tokenCodec;
    private final CompanyUserRepository userRepository;

    public SessionTokenFilter(TokenCodec tokenCodec, CompanyUserRepository userRepository) {
        this.tokenCodec = tokenCodec;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        Map<String, Object> claims = null;
        if (header != null && header.startsWith("Bearer ")) {
            claims = tokenCodec.verifyTierToken(header.substring(7));
        }
        if (claims == null) {
            log.debug("SessionTokenFilter: no valid claims (header present: {})", header != null);
            // Delegate to the entry point which renders the canonical 401 JSON body.
            chain.doFilter(request, response);
            return;
        }

        Object sub = claims.get("sub");
        CompanyUser user = null;
        if (sub instanceof String subStr) {
            try {
                // Eager fetch: the filter runs outside a transaction (open-in-view disabled).
                user = userRepository.findWithAccountsById(UUID.fromString(subStr)).orElse(null);
            } catch (IllegalArgumentException ignored) {
                user = null;
            }
        }
        if (user == null) {
            log.debug("SessionTokenFilter: no user for sub {}", sub);
            chain.doFilter(request, response);
            return;
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("Rejected request from non-active user {} (status={})", user.getEmail(), user.getStatus());
            chain.doFilter(request, response);
            return;
        }

        AuthPrincipal principal = new AuthPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getCompany() == null ? null : user.getCompany().getId(),
                user.getApprovalLimit(),
                user.getAssignedAccounts().stream().map(a -> a.getId()).collect(Collectors.toUnmodifiableSet()),
                false);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null,
                        java.util.List.of(() -> principal.role().securityRole()));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
