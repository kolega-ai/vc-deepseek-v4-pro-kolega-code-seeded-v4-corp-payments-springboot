package com.northbridge.coremock.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Core-banking credentials, bound exclusively from environment variables.
 *
 * <p>By design there is NO default value for either credential: a blank
 * {@code CORE_MOCK_API_KEY} or {@code CORE_SCOPE_SECRET} aborts startup with an
 * explicit message so a misconfigured tier fails closed instead of accepting
 * requests with a guessable credential. The datasource password default in
 * application.yml is a documented local-development value only.
 */
@ConfigurationProperties(prefix = "app.env")
public class AppEnvProperties implements InitializingBean {

    /** API credential callers must present in the X-Core-Api-Key header. */
    private String coreApiKey;

    /** Shared HMAC secret used to verify payment-api scope tokens. */
    private String coreScopeSecret;

    @Override
    public void afterPropertiesSet() {
        if (coreApiKey == null || coreApiKey.isBlank()) {
            throw new IllegalStateException(
                    "CORE_MOCK_API_KEY environment variable is required and must not be blank. "
                            + "The core-banking credential is only accepted from the environment; no default exists.");
        }
        if (coreApiKey.length() < 16) {
            throw new IllegalStateException(
                    "CORE_MOCK_API_KEY must be at least 16 characters long (received " + coreApiKey.length() + ").");
        }
        if (coreScopeSecret == null || coreScopeSecret.isBlank()) {
            throw new IllegalStateException(
                    "CORE_SCOPE_SECRET environment variable is required and must not be blank. "
                            + "It is the shared HMAC secret for scope tokens minted by the payment-api tier.");
        }
        if (coreScopeSecret.length() < 32) {
            throw new IllegalStateException(
                    "CORE_SCOPE_SECRET must be at least 32 characters long (received " + coreScopeSecret.length() + ").");
        }
    }

    public String getCoreApiKey() {
        return coreApiKey;
    }

    public void setCoreApiKey(String coreApiKey) {
        this.coreApiKey = coreApiKey;
    }

    public String getCoreScopeSecret() {
        return coreScopeSecret;
    }

    public void setCoreScopeSecret(String coreScopeSecret) {
        this.coreScopeSecret = coreScopeSecret;
    }
}
