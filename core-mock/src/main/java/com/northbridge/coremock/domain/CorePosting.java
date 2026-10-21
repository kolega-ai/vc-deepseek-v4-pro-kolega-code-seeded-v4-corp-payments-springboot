package com.northbridge.coremock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable ledger posting executed against a core account. One posting per
 * released payment (or reversal); {@code request_ref} is the payment tier's
 * idempotency key.
 */
@Entity
@Table(name = "core_posting",
        indexes = @Index(name = "idx_core_posting_request_ref", columnList = "request_ref"))
public class CorePosting extends BaseEntity {

    @Column(name = "core_ref", nullable = false, unique = true, length = 40)
    private String coreRef;

    @Column(name = "request_ref", nullable = false, length = 40)
    private String requestRef;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "posting_type", nullable = false, length = 20)
    private String postingType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "counterparty", length = 120)
    private String counterparty;

    @Column(name = "response_code", length = 4)
    private String responseCode;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "posted_at")
    private Instant postedAt;

    public String getCoreRef() {
        return coreRef;
    }

    public void setCoreRef(String coreRef) {
        this.coreRef = coreRef;
    }

    public String getRequestRef() {
        return requestRef;
    }

    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getPostingType() {
        return postingType;
    }

    public void setPostingType(String postingType) {
        this.postingType = postingType;
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

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }
}
