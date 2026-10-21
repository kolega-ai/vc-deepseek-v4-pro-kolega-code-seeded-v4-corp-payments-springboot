package com.northbridge.coremock.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /internal/accounts} — a bank-scope-only endpoint
 * used to open a new core account on behalf of a company.
 */
public record CreateAccountRequest(
        @NotBlank(message = "companyId is required")
        String companyId,

        @NotBlank(message = "accountNumber is required")
        @Size(max = 20, message = "accountNumber must be at most 20 characters")
        String accountNumber,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
        String currency,

        @Size(max = 32, message = "product must be at most 32 characters")
        String product,

        @NotNull(message = "openingBalance is required")
        BigDecimal openingBalance) {
}
