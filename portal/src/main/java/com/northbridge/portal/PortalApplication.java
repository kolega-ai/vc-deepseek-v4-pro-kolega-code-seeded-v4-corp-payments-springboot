package com.northbridge.portal;

import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.repo.PortalUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * portal — the CHANNEL tier of the Corporate Payments Portal.
 *
 * <p>This tier AUTHENTICATES users (session-cookie login) and renders the server-side UI. It makes
 * NO authorization decisions of record: every business request is forwarded to the payment-api
 * decision tier with an opaque signed session token, and payment-api resolves the acting user,
 * role, company scope and approval limit from its own database on every request.</p>
 */
@SpringBootApplication
@EnableScheduling
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }

    /**
     * Guarantees the bank's operations desk has a working support login on every deployment,
     * including fresh environments where the demo dataset has not been loaded.
     */
    @Bean
    CommandLineRunner ensureOpsSupportAccount(PortalUserRepository users, PasswordEncoder encoder) {
        return args -> {
            String email = "ops.support@bank.example";
            if (users.findByEmailIgnoreCase(email).isEmpty()) {
                PortalUser support = new PortalUser(UUID.randomUUID(), email, "Ops Support",
                        "bank_admin", "ACTIVE", null, "Northbridge Bank", BigDecimal.ZERO);
                support.setPasswordHash(encoder.encode("Northbridge#2026"));
                users.save(support);
            }
        };
    }
}
