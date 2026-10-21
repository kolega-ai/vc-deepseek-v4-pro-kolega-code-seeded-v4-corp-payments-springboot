package com.northbridge.paymentapi.domain;

/**
 * Payment lifecycle. Only the transitions enumerated in {@link com.northbridge.paymentapi.state.PaymentStateMachine}
 * are allowed; anything not listed is forbidden.
 */
public enum PaymentStatus {
    DRAFT,
    PENDING_APPROVAL,
    PARTIALLY_APPROVED,
    APPROVED,
    QUEUED_FOR_RELEASE,
    RELEASED,
    REJECTED,
    RETURNED,
    FAILED,
    REVERSED
}
