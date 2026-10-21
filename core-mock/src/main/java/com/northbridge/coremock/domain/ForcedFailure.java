package com.northbridge.coremock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Deliberately injected failure for a payment requestRef, used to demo the
 * retry journey: the next {@code attemptsRemaining} postings for this
 * requestRef are declined with response code 91 without touching any account.
 */
@Entity
@Table(name = "forced_failure")
public class ForcedFailure {

    @Id
    @Column(name = "request_ref", length = 40)
    private String requestRef;

    @Column(name = "attempts_remaining", nullable = false)
    private int attemptsRemaining;

    @Column(name = "note", length = 200)
    private String note;

    public String getRequestRef() {
        return requestRef;
    }

    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }

    public int getAttemptsRemaining() {
        return attemptsRemaining;
    }

    public void setAttemptsRemaining(int attemptsRemaining) {
        this.attemptsRemaining = attemptsRemaining;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
