package com.northbridge.paymentapi.domain;

/** Payment rail derived from the beneficiary currency; determines the fee schedule row. */
public enum PaymentRail {
    FASTER_PAYMENTS, SEPA, SWIFT
}
