package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment beneficiary. A beneficiary in PENDING_COOLING may not receive a payment; amending its
 * account number (or bank code) resets {@link #coolingUntil}.
 */
@Entity
@Table(name = "beneficiary",
        uniqueConstraints = @UniqueConstraint(name = "uq_beneficiary_account", columnNames = {"company_id", "account_number", "bank_code"}))
public class Beneficiary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    @Column(name = "bank_code", nullable = false, length = 34)
    private String bankCode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BeneficiaryStatus status;

    @Column(name = "cooling_until")
    private Instant coolingUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private CompanyUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_amended_at")
    private Instant lastAmendedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_amended_by")
    private CompanyUser lastAmendedBy;

    protected Beneficiary() {
    }

    public Beneficiary(UUID id, Company company, String name, String accountNumber, String bankCode,
                       String currency, BeneficiaryStatus status, Instant coolingUntil, CompanyUser createdBy) {
        setId(id);
        this.company = company;
        this.name = name;
        this.accountNumber = accountNumber;
        this.bankCode = bankCode;
        this.currency = currency;
        this.status = status;
        this.coolingUntil = coolingUntil;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BeneficiaryStatus getStatus() { return status; }
    public void setStatus(BeneficiaryStatus status) { this.status = status; }
    public Instant getCoolingUntil() { return coolingUntil; }
    public void setCoolingUntil(Instant coolingUntil) { this.coolingUntil = coolingUntil; }
    public CompanyUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(CompanyUser createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastAmendedAt() { return lastAmendedAt; }
    public void setLastAmendedAt(Instant lastAmendedAt) { this.lastAmendedAt = lastAmendedAt; }
    public CompanyUser getLastAmendedBy() { return lastAmendedBy; }
    public void setLastAmendedBy(CompanyUser lastAmendedBy) { this.lastAmendedBy = lastAmendedBy; }
}
