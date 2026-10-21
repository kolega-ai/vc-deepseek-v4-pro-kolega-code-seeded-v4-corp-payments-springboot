package com.northbridge.coremock.web.dto;

import com.northbridge.coremock.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentStatusResponse(String coreRef, String status, BigDecimal amount, String currency, Instant postedAt) {

    public static PaymentStatusResponse from(PaymentStatus status) {
        return new PaymentStatusResponse(status.getCoreRef(), status.getStatus(),
                status.getAmount(), status.getCurrency(), status.getPostedAt());
    }
}
