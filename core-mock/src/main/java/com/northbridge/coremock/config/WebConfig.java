package com.northbridge.coremock.config;

import com.northbridge.coremock.security.ScopeAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies the scope-authentication interceptor to every business endpoint.
 *
 * <p>{@code /actuator/**} is intentionally excluded: health and readiness
 * probes must remain reachable by orchestration tooling without core-banking
 * credentials. Every {@code /v1/**} and {@code /internal/**} route requires a
 * valid {@code X-Core-Api-Key} and a verifiable {@code X-Scope-Token}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ScopeAuthInterceptor scopeAuthInterceptor;

    public WebConfig(ScopeAuthInterceptor scopeAuthInterceptor) {
        this.scopeAuthInterceptor = scopeAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(scopeAuthInterceptor)
                .addPathPatterns("/v1/**", "/internal/**");
    }
}
