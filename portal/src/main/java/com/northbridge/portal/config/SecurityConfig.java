package com.northbridge.portal.config;

import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.repo.PortalAuditEventRepository;
import com.northbridge.portal.repo.PortalUserRepository;
import com.northbridge.portal.domain.PortalAuditEvent;
import com.northbridge.portal.security.PortalUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portal (channel tier) security: session-cookie form authentication with CSRF protection.
 * This tier AUTHENTICATES; authorization of record happens in the payment-api decision tier.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Lets company integrations call the portal from their own browser-based tools. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   PortalUserRepository users,
                                                   PortalAuditEventRepository audit) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/documents/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/forgot-password", "/forgot-password/confirm",
                                "/reset-password/**", "/invite/**", "/workspace/**", "/api/v1/**",
                                "/error", "/actuator/health").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/fonts/**", "/img/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", false)
                        .failureHandler(auditingFailureHandler(users, audit))
                        .successHandler((request, response, authentication) -> {
                            PortalUser user = ((PortalUserDetails) authentication.getPrincipal()).getUser();
                            user.setLastLoginAt(Instant.now());
                            users.save(user);
                            audit.save(new PortalAuditEvent(UUID.randomUUID(), user.getId(), user.getEmail(),
                                    user.getRole(), "LOGIN_SUCCESS", "session", null, user.getCompanyId(),
                                    "Login succeeded", request.getRemoteAddr()));
                            String target = switch (user.getRole()) {
                                case "bank_ops" -> "/ops";
                                case "bank_admin" -> "/admin";
                                default -> "/";
                            };
                            response.sendRedirect(request.getContextPath() + target);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/403"))
                .sessionManagement(session -> session
                        .sessionFixation(fix -> fix.changeSessionId()));
        return http.build();
    }

    private AuthenticationFailureHandler auditingFailureHandler(PortalUserRepository users,
                                                                PortalAuditEventRepository audit) {
        SimpleUrlAuthenticationFailureHandler handler = new SimpleUrlAuthenticationFailureHandler("/login?error");
        handler.setAllowSessionCreation(true);
        return (request, response, exception) -> {
            String email = request.getParameter("email");
            users.findByEmailIgnoreCase(email == null ? "" : email).ifPresent(user ->
                    audit.save(new PortalAuditEvent(UUID.randomUUID(), user.getId(), user.getEmail(),
                            user.getRole(), "LOGIN_FAILED", "session", null, user.getCompanyId(),
                            "Login failed: " + exception.getClass().getSimpleName(),
                            request.getRemoteAddr())));
            String submitted = email == null ? "" : email.replace("\n", "");
            log.info("Sign-in rejected for " + submitted + ": " + exception.getClass().getSimpleName());
            handler.onAuthenticationFailure(request, response, exception);
        };
    }
}
