package com.northbridge.coremock.domain;

/**
 * Posting types allowed by core-mock, mirroring the CHECK constraint on
 * {@code core_posting.posting_type}.
 */
public enum PostingType {

    /** Money leaves the account (payment or reversal of a previous credit). */
    DEBIT,
    /** Money enters the account. */
    CREDIT,
    /** Reversal credit: restores money debited by an earlier payment. */
    REVERSAL_CREDIT,
    /** Reversal debit: removes money credited by an earlier transaction. */
    REVERSAL_DEBIT;

    public boolean isDebit() {
        return this == DEBIT || this == REVERSAL_DEBIT;
    }

    /**
     * Strict parser: returns {@code null} for anything not in this enum so the
     * caller can answer with a 400 VALIDATION error instead of an unchecked
     * exception.
     */
    public static PostingType fromRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (PostingType type : values()) {
            if (type.name().equals(raw.trim())) {
                return type;
            }
        }
        return null;
    }
}
