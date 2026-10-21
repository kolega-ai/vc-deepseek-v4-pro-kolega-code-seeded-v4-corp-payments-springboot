package com.northbridge.coremock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Vendor core-banking stub tier of the Corporate Payments Portal.
 *
 * <p>core-mock behaves like an external core banking system: it exposes its own
 * network endpoint (port 8082 by default), requires its own credential
 * ({@code X-Core-Api-Key}, compared in constant time) and verifies short-lived
 * scope tokens minted by the payment-api decision tier before honoring any
 * business request. It never trusts account or company identifiers carried in
 * request bodies, paths, or query parameters for authorization.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreMockApplication.class, args);
    }
}
