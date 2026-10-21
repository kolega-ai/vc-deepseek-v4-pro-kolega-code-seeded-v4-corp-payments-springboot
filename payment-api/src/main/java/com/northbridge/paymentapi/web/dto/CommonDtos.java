package com.northbridge.paymentapi.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Notification and misc DTOs. */
public final class CommonDtos {

    private CommonDtos() {
    }

    public record NotificationView(UUID id, String type, String message, String link,
                                   Instant createdAt, Instant readAt) {
    }

    public record StatusLookup(String paymentRef, String status, java.math.BigDecimal amount,
                               String currency, java.math.BigDecimal fee,
                               java.math.BigDecimal settlementAmount, java.time.LocalDate valueDate,
                               String debitAccountNumber, String beneficiaryAccountNumber,
                               Instant updatedAt) {
    }

    public record ReleaseCandidateView(UUID paymentId, String paymentRef, String coreRef,
                                       java.math.BigDecimal postedAmount, String accountNumber) {
    }

    public record WebhookDeliveryView(UUID id, UUID paymentId, String paymentRef, String url,
                                      String status, int attempts, String responseCode,
                                      Instant createdAt) {
    }
}
