package com.northbridge.coremock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Query-side status of a payment known to core, keyed by the core reference.
 */
@Entity
@Table(name = "payment_status")
public class PaymentStatus {

    @Id
    @Column(name = "core_ref", length = 40)
    private String coreRef;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "posted_at")
    private Instant postedAt;

    public String getCoreRef() {
        return coreRef;
    }

    public void setCoreRef(String coreRef) {
        this.coreRef = coreRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }
}
