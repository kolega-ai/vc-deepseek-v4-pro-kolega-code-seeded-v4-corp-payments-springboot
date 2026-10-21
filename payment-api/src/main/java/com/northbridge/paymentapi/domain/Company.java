package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company")
public class Company extends BaseEntity {

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "registration_number", nullable = false, length = 40)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    /** Company-level daily limit in the base currency. Fixed-precision decimal. */
    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Company() {
    }

    public Company(UUID id, String legalName, String registrationNumber, String baseCurrency, BigDecimal dailyLimit) {
        setId(id);
        this.legalName = legalName;
        this.registrationNumber = registrationNumber;
        this.baseCurrency = baseCurrency;
        this.dailyLimit = dailyLimit;
        this.status = CompanyStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public CompanyStatus getStatus() { return status; }
    public void setStatus(CompanyStatus status) { this.status = status; }
    public String getBaseCurrency() { return baseCurrency; }
    public void setBaseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
