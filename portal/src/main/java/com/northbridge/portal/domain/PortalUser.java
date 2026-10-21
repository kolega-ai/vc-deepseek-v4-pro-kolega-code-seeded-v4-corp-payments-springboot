package com.northbridge.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Portal-side user record (authentication tier). The role/company/limit values are a mirror of the
 * payment-api decision tier, which is authoritative for authorization. Passwords are stored as
 * BCrypt hashes; INVITED users have no password until they accept their invite.
 */
@Entity
@Table(name = "portal_user")
public class PortalUser {

    @Id
    @Column(nullable = false, length = 36)
    private UUID id;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "company_id", length = 36)
    private UUID companyId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "approval_limit", nullable = false, precision = 19, scale = 4)
    private java.math.BigDecimal approvalLimit;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "portal_user_account", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "account_id")
    private Set<UUID> accountIds = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "signing_digest", length = 200)
    private String signingDigest;

    protected PortalUser() {
    }

    public PortalUser(UUID id, String email, String fullName, String role, String status,
                      UUID companyId, String companyName, java.math.BigDecimal approvalLimit) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.companyId = companyId;
        this.companyName = companyName;
        this.approvalLimit = approvalLimit;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public java.math.BigDecimal getApprovalLimit() { return approvalLimit; }
    public void setApprovalLimit(java.math.BigDecimal approvalLimit) { this.approvalLimit = approvalLimit; }
    public Set<UUID> getAccountIds() { return accountIds; }
    public void setAccountIds(Set<UUID> accountIds) { this.accountIds = accountIds; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getSigningDigest() { return signingDigest; }
    public void setSigningDigest(String signingDigest) { this.signingDigest = signingDigest; }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isInvited() {
        return "INVITED".equals(status);
    }
}
