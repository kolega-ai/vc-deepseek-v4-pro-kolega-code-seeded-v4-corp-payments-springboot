package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One release attempt (and its outcome) for a payment. Release is idempotent per payment: a payment
 * that is already released/reversed can never be posted again.
 */
@Entity
@Table(name = "release_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_release_payment", columnNames = "payment_id"))
public class Release extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** Core reference returned by a successful posting. */
    @Column(name = "core_ref", length = 40)
    private String coreRef;

    @Column(name = "posted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal postedAmount;

    @Column(name = "response_code", length = 4)
    private String responseCode;

    /** Human reason for a failed release, e.g. insufficient_funds / forced_failure / core_error. */
    @Column(length = 64)
    private String reason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "investigation_status", nullable = false, length = 20)
    private ReleaseInvestigationStatus investigationStatus;

    @Column(name = "investigation_notes", length = 1000)
    private String investigationNotes;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected Release() {
    }

    public Release(UUID id, Payment payment, BigDecimal postedAmount) {
        setId(id);
        this.payment = payment;
        this.postedAmount = postedAmount;
        this.retryCount = 0;
        this.investigationStatus = ReleaseInvestigationStatus.NONE;
        this.attemptedAt = Instant.now();
    }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public String getCoreRef() { return coreRef; }
    public void setCoreRef(String coreRef) { this.coreRef = coreRef; }
    public BigDecimal getPostedAmount() { return postedAmount; }
    public void setPostedAmount(BigDecimal postedAmount) { this.postedAmount = postedAmount; }
    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public ReleaseInvestigationStatus getInvestigationStatus() { return investigationStatus; }
    public void setInvestigationStatus(ReleaseInvestigationStatus investigationStatus) {
        this.investigationStatus = investigationStatus;
    }
    public String getInvestigationNotes() { return investigationNotes; }
    public void setInvestigationNotes(String investigationNotes) { this.investigationNotes = investigationNotes; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
}
