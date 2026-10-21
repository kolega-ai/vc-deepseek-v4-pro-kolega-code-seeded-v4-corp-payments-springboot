package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "company_user",
        uniqueConstraints = @UniqueConstraint(name = "uq_company_user_email", columnNames = "email"))
public class CompanyUser extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** Server-side approval limit. Only company_checker users exercise it; it is never read from a request. */
    @Column(name = "approval_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal approvalLimit;

    /** Null for bank users (bank_ops / bank_admin), which are bank-wide roles. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "company_user_account",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "account_id"))
    private Set<Account> assignedAccounts = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyUser() {
    }

    public CompanyUser(UUID id, String email, String fullName, Role role, UserStatus status,
                       BigDecimal approvalLimit, Company company) {
        setId(id);
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.approvalLimit = approvalLimit;
        this.company = company;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public BigDecimal getApprovalLimit() { return approvalLimit; }
    public void setApprovalLimit(BigDecimal approvalLimit) { this.approvalLimit = approvalLimit; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Set<Account> getAssignedAccounts() { return assignedAccounts; }
    public void setAssignedAccounts(Set<Account> assignedAccounts) { this.assignedAccounts = assignedAccounts; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isRestrictedToAccounts() {
        return !assignedAccounts.isEmpty();
    }

    public boolean mayAccessAccount(Account account) {
        if (assignedAccounts.isEmpty()) {
            return true;
        }
        return assignedAccounts.stream().anyMatch(a -> a.getId().equals(account.getId()));
    }
}
