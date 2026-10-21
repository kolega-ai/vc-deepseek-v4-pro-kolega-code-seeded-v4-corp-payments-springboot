package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payment_amendment")
public class PaymentAmendment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private CompanyUser actor;

    /** List of {field, from, to} entries serialised as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", nullable = false, columnDefinition = "jsonb")
    private List<ChangedField> changedFields = new ArrayList<>();

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected PaymentAmendment() {
    }

    public PaymentAmendment(UUID id, Payment payment, CompanyUser actor, List<ChangedField> changedFields) {
        setId(id);
        this.payment = payment;
        this.actor = actor;
        this.changedFields = changedFields;
        this.changedAt = Instant.now();
    }

    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public CompanyUser getActor() { return actor; }
    public void setActor(CompanyUser actor) { this.actor = actor; }
    public List<ChangedField> getChangedFields() { return changedFields; }
    public void setChangedFields(List<ChangedField> changedFields) { this.changedFields = changedFields; }
    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public record ChangedField(String field, String from, String to) {
    }
}
