package com.northbridge.paymentapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * payment-api — the DECISION tier of the Corporate Payments Portal.
 *
 * <p>This tier OWNS authorization. It resolves the opaque, signed session token forwarded by the
 * portal (channel) tier, loads the acting user, role, company scope and approval limit FROM ITS OWN
 * DATABASE on every request, and is the authoritative source of every authorization outcome.
 * It ignores any user id, role, company or limit supplied in a header, path, query, form field or body.</p>
 */
@SpringBootApplication
public class PaymentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApiApplication.class, args);
    }
}
