package com.northbridge.coremock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.util.UUID;

/**
 * Base class for all core-mock entities.
 *
 * <p>Identifiers are UUIDs assigned client-side immediately before insert via
 * {@link #assignId()} so references can be established without a database
 * round-trip. The assignment only happens when the id is still {@code null},
 * so callers may pre-set an id if ever required.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }
}
