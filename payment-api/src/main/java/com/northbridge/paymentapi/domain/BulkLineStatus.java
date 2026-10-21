package com.northbridge.paymentapi.domain;

/** INVALID is terminal; every other state is produced by a stated workflow action. */
public enum BulkLineStatus {
    VALID, INVALID, APPROVED, RELEASED, REJECTED
}
