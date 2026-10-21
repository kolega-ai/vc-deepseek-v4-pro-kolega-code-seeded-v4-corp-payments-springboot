package com.northbridge.coremock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northbridge.coremock.config.AppEnvProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Gatekeeper for every business endpoint ({@code /v1/**}, {@code /internal/**}).
 *
 * <p>Two credentials are required on every request:
 * <ul>
 *   <li>{@code X-Core-Api-Key} — core-mock's own credential, compared with
 *       {@link MessageDigest#isEqual} so the comparison is constant-time.</li>
 *   <li>{@code X-Scope-Token} — a short-lived token minted by payment-api,
 *       verified via {@link ScopeTokenVerifier}.</li>
 * </ul>
 * Either failure produces 401 {@code {"error":{"code":"CORE_AUTH_FAILED",...}}}.
 * On success the verified {@link ScopeToken} is stored in a request attribute
 * that controllers read — controllers never re-parse headers or re-derive scope.
 */
@Component
public class ScopeAuthInterceptor implements HandlerInterceptor {

    /** Request attribute under which the verified ScopeToken is stored. */
    public static final String SCOPE_TOKEN_ATTRIBUTE = "scopeToken";

    private static final Logger log = LoggerFactory.getLogger(ScopeAuthInterceptor.class);
    private static final String API_KEY_HEADER = "X-Core-Api-Key";
    private static final String SCOPE_TOKEN_HEADER = "X-Scope-Token";

    private final AppEnvProperties properties;
    private final ScopeTokenVerifier tokenVerifier;
    private final ObjectMapper objectMapper;

    public ScopeAuthInterceptor(AppEnvProperties properties, ScopeTokenVerifier tokenVerifier, ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenVerifier = tokenVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!constantTimeEquals(properties.getCoreApiKey(), apiKey)) {
            log.warn("core-mock rejected {} {}: missing or invalid X-Core-Api-Key",
                    request.getMethod(), request.getRequestURI());
            writeAuthFailure(response, "Missing or invalid X-Core-Api-Key");
            return false;
        }

        ScopeToken scopeToken = tokenVerifier.verify(request.getHeader(SCOPE_TOKEN_HEADER));
        if (scopeToken == null) {
            log.warn("core-mock rejected {} {}: missing, invalid or expired X-Scope-Token",
                    request.getMethod(), request.getRequestURI());
            writeAuthFailure(response, "Missing, invalid or expired X-Scope-Token");
            return false;
        }

        request.setAttribute(SCOPE_TOKEN_ATTRIBUTE, scopeToken);
        return true;
    }

    /**
     * Constant-time comparison of the configured API key with the presented
     * header value. A missing header compares against the real key so the
     * comparison never short-circuits on value-dependent length information.
     */
    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            provided = "";
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private void writeAuthFailure(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", Map.of("code", "CORE_AUTH_FAILED", "message", message)));
    }
}
