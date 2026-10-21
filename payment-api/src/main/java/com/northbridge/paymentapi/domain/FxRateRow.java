package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Seeded reference data: FX rates. A rate expresses units of the target currency per 1 unit of the
 * base (e.g. GBP/EUR = 1.1650 means 1 GBP buys 1.1650 EUR). Rates are read server-side only and are
 * NEVER accepted from a client.
 */
@Entity
@Table(name = "fx_rate")
public class FxRateRow extends BaseEntity {

    /** Currency pair in BASE/QUOTE form, e.g. "GBP/EUR". */
    @Column(name = "currency_pair", nullable = false, length = 7)
    private String currencyPair;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    protected FxRateRow() {
    }

    public FxRateRow(UUID id, String currencyPair, BigDecimal rate, Instant effectiveAt) {
        setId(id);
        this.currencyPair = currencyPair;
        this.rate = rate;
        this.effectiveAt = effectiveAt;
    }

    public String getCurrencyPair() { return currencyPair; }
    public void setCurrencyPair(String currencyPair) { this.currencyPair = currencyPair; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }
}
