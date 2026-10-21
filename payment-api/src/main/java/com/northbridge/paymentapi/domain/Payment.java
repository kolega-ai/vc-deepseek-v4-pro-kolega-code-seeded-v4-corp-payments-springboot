package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single payment instruction. Amounts are fixed-precision decimals.
 * fee/fxRate/settlementAmount are computed server-side from the seeded fee schedule and FX rate
 * table and are never accepted from a client.
 */
@Entity
@Table(name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uq_payment_ref", columnNames = "ref"),
        indexes = {
                @Index(name = "ix_payment_company_status", columnList = "company_id, status"),
                @Index(name = "ix_payment_status_value_date", columnList = "status, value_date"),
                @Index(name = "ix_payment_company_created", columnList = "company_id, created_at")
        })
public class Payment extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String ref;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debit_account_id", nullable = false)
    private Account debitAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Currency of the debit account / payment amount. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Server-computed fee in the payment (debit) currency. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee;

    /** Server-selected FX rate (1 for same-currency). */
    @Column(name = "fx_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal fxRate;

    /** Server-computed settlement amount in the beneficiary currency. */
    @Column(name = "settlement_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentRail productType;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(nullable = false, length = 140)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status;

    @Column(length = 64)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private CompanyUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amended_by")
    private CompanyUser amendedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_file_id")
    private BulkFile bulkFile;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("actedAt ASC")
    private List<ApprovalAction> approvalActions = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<PaymentAmendment> amendments = new ArrayList<>();

    protected Payment() {
    }

    public Payment(UUID id, String ref, Company company, Account debitAccount, Beneficiary beneficiary,
                   BigDecimal amount, String currency, LocalDate valueDate, String reference,
                   PaymentStatus status, CompanyUser createdBy) {
        setId(id);
        this.ref = ref;
        this.company = company;
        this.debitAccount = debitAccount;
        this.beneficiary = beneficiary;
        this.amount = amount;
        this.currency = currency;
        this.valueDate = valueDate;
        this.reference = reference;
        this.status = status;
        this.createdBy = createdBy;
        this.fee = BigDecimal.ZERO;
        this.fxRate = BigDecimal.ONE;
        this.settlementAmount = amount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Account getDebitAccount() { return debitAccount; }
    public void setDebitAccount(Account debitAccount) { this.debitAccount = debitAccount; }
    public Beneficiary getBeneficiary() { return beneficiary; }
    public void setBeneficiary(Beneficiary beneficiary) { this.beneficiary = beneficiary; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public BigDecimal getFxRate() { return fxRate; }
    public void setFxRate(BigDecimal fxRate) { this.fxRate = fxRate; }
    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public void setSettlementAmount(BigDecimal settlementAmount) { this.settlementAmount = settlementAmount; }
    public PaymentRail getProductType() { return productType; }
    public void setProductType(PaymentRail productType) { this.productType = productType; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public CompanyUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(CompanyUser createdBy) { this.createdBy = createdBy; }
    public CompanyUser getAmendedBy() { return amendedBy; }
    public void setAmendedBy(CompanyUser amendedBy) { this.amendedBy = amendedBy; }
    public BulkFile getBulkFile() { return bulkFile; }
    public void setBulkFile(BulkFile bulkFile) { this.bulkFile = bulkFile; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public List<ApprovalAction> getApprovalActions() { return approvalActions; }
    public List<PaymentAmendment> getAmendments() { return amendments; }

    public BigDecimal debitTotal() {
        return amount.add(fee);
    }
}
