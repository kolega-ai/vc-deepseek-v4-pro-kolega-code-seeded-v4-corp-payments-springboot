package com.northbridge.coremock.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Response body for {@code POST /v1/postings}. Nullable fields are omitted from
 * the JSON payload unless relevant for the outcome ({@code coreRef} is always
 * serialized, even when null, because callers key on it).
 */
public record PostingResponse(
        String coreRef,
        String status,
        String responseCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal balance,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean duplicate) {

    public static PostingResponse posted(String coreRef, BigDecimal balance) {
        return new PostingResponse(coreRef, "POSTED", "00", null, balance, null);
    }

    public static PostingResponse duplicate(String coreRef) {
        return new PostingResponse(coreRef, "POSTED", "00", null, null, Boolean.TRUE);
    }

    public static PostingResponse forcedFailure() {
        return new PostingResponse(null, "FORCED_FAILURE", "91", "Forced decline", null, null);
    }

    public static PostingResponse insufficientFunds() {
        return new PostingResponse(null, "INSUFFICIENT_FUNDS", "51", null, null, null);
    }
}
