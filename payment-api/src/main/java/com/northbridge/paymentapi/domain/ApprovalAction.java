package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_action")
public class ApprovalAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private CompanyUser actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Decision decision;

    @Column(length = 500)
    private String comment;

    /** The server-side approval limit that applied to this action. */
    @Column(name = "limit_applied", nullable = false, precision = 19, scale = 4)
    private BigDecimal limitApplied;

    /** True when this action covered the half amount of the dual-approval path. */
    @Column(name = "half_required", nullable = false)
    private boolean halfRequired;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    protected ApprovalAction() {
    }

    public ApprovalAction(UUID id, Payment payment, CompanyUser actor, Decision decision,
                          String comment, BigDecimal limitApplied, boolean halfRequired) {
        setId(id);
        this.payment = payment;
        this.actor = actor;
        this.decision = decision;
        this.comment = comment;
        this.limitApplied = limitApplied;
        this.halfRequired = halfRequired;
        this.actedAt = Instant.now();
    }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public CompanyUser getActor() { return actor; }
    public void setActor(CompanyUser actor) { this.actor = actor; }
    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public BigDecimal getLimitApplied() { return limitApplied; }
    public void setLimitApplied(BigDecimal limitApplied) { this.limitApplied = limitApplied; }
    public boolean isHalfRequired() { return halfRequired; }
    public void setHalfRequired(boolean halfRequired) { this.halfRequired = halfRequired; }
    public Instant getActedAt() { return actedAt; }
    public void setActedAt(Instant actedAt) { this.actedAt = actedAt; }

    public enum Decision {
        APPROVED, REJECTED, RETURNED
    }
}
