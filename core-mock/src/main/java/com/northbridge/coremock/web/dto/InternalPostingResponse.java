package com.northbridge.coremock.web.dto;

import com.northbridge.coremock.domain.CorePosting;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reconcilable posting view for the internal (bank-scope) API.
 */
public record InternalPostingResponse(
        String coreRef,
        String requestRef,
        String accountNumber,
        String postingType,
        BigDecimal amount,
        String currency,
        Instant postedAt) {

    public static InternalPostingResponse from(CorePosting posting) {
        return new InternalPostingResponse(posting.getCoreRef(), posting.getRequestRef(),
                posting.getAccountNumber(), posting.getPostingType(), posting.getAmount(),
                posting.getCurrency(), posting.getPostedAt());
    }
}
