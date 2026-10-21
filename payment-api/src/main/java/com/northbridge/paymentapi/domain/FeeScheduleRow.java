package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Seeded reference data: fee schedule by payment rail and debit currency.
 * Values are read server-side only and are NEVER accepted from a client.
 */
@Entity
@Table(name = "fee_schedule")
public class FeeScheduleRow extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private PaymentRail productType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "flat_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal flatFee;

    /** Percentage fee expressed as a percent, e.g. 0.10 = 0.10%. */
    @Column(name = "pct_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal pctFee;

    @Column(name = "min_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal minFee;

    @Column(name = "max_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxFee;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    protected FeeScheduleRow() {
    }

    public FeeScheduleRow(UUID id, PaymentRail productType, String currency, BigDecimal flatFee,
                          BigDecimal pctFee, BigDecimal minFee, BigDecimal maxFee, Instant effectiveFrom) {
        setId(id);
        this.productType = productType;
        this.currency = currency;
        this.flatFee = flatFee;
        this.pctFee = pctFee;
        this.minFee = minFee;
        this.maxFee = maxFee;
        this.effectiveFrom = effectiveFrom;
    }

    public PaymentRail getProductType() { return productType; }
    public void setProductType(PaymentRail productType) { this.productType = productType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getFlatFee() { return flatFee; }
    public void setFlatFee(BigDecimal flatFee) { this.flatFee = flatFee; }
    public BigDecimal getPctFee() { return pctFee; }
    public void setPctFee(BigDecimal pctFee) { this.pctFee = pctFee; }
    public BigDecimal getMinFee() { return minFee; }
    public void setMinFee(BigDecimal minFee) { this.minFee = minFee; }
    public BigDecimal getMaxFee() { return maxFee; }
    public void setMaxFee(BigDecimal maxFee) { this.maxFee = maxFee; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }
}
