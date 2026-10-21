package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Company account (the debit side of a payment). Balances are held by the vendor core system;
 * this tier stores only the account registry entry used for authorization scoping.
 */
@Entity
@Table(name = "account",
        uniqueConstraints = @UniqueConstraint(name = "uq_account_number", columnNames = "account_number"))
public class Account extends BaseEntity {

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 32)
    private String product;

    @Column(length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(UUID id, String accountNumber, Company company, String currency, String product, String name) {
        setId(id);
        this.accountNumber = accountNumber;
        this.company = company;
        this.currency = currency;
        this.product = product;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
