package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reversal")
public class Reversal extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReversalStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private CompanyUser requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private CompanyUser approvedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "core_ref", length = 40)
    private String coreRef;

    @Column(name = "posted_at")
    private Instant postedAt;

    protected Reversal() {
    }

    public Reversal(UUID id, Payment payment, String reason, CompanyUser requestedBy) {
        setId(id);
        this.payment = payment;
        this.reason = reason;
        this.status = ReversalStatus.REQUESTED;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
    }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public ReversalStatus getStatus() { return status; }
    public void setStatus(ReversalStatus status) { this.status = status; }
    public CompanyUser getRequestedBy() { return requestedBy; }
    public void setRequestedBy(CompanyUser requestedBy) { this.requestedBy = requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public CompanyUser getApprovedBy() { return approvedBy; }
    public void setApprovedBy(CompanyUser approvedBy) { this.approvedBy = approvedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public String getCoreRef() { return coreRef; }
    public void setCoreRef(String coreRef) { this.coreRef = coreRef; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
}
