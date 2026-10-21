package com.northbridge.coremock.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /v1/postings}. The account number here is
 * treated as untrusted input: authorization comes exclusively from the verified
 * scope token, which must include this account (or carry a bank scope).
 */
public record PostingRequest(
        @NotBlank(message = "requestRef is required")
        @Size(max = 40, message = "requestRef must be at most 40 characters")
        String requestRef,

        @NotBlank(message = "accountNumber is required")
        @Size(max = 20, message = "accountNumber must be at most 20 characters")
        String accountNumber,

        @NotBlank(message = "postingType is required")
        @Size(max = 20, message = "postingType must be at most 20 characters")
        String postingType,

        @NotNull(message = "amount is required")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
        String currency,

        @Size(max = 120, message = "counterparty must be at most 120 characters")
        String counterparty) {
}
