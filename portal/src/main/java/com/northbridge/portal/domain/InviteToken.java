package com.northbridge.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Single-use, time-bound (7 day) invite acceptance token. Only the SHA-256 hash is stored. */
@Entity
@Table(name = "invite_token")
public class InviteToken {

    @Id
    @Column(nullable = false, length = 36)
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InviteToken() {
    }

    public InviteToken(UUID id, String tokenHash, UUID userId, String email, UUID companyId,
                       String companyName, String role, Instant expiresAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.email = email;
        this.companyId = companyId;
        this.companyName = companyName;
        this.role = role;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
