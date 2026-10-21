package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bulk_file_line",
        indexes = @Index(name = "ix_bulk_line_file", columnList = "bulk_file_id, line_number"))
public class BulkFileLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bulk_file_id", nullable = false)
    private BulkFile bulkFile;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    /** Raw CSV row as uploaded; parsed during processing. */
    @Column(name = "raw_content", nullable = false, length = 1000)
    private String rawContent;

    @Column(name = "debit_account_number", length = 20)
    private String debitAccountNumber;

    @Column(name = "beneficiary_account_number", length = 34)
    private String beneficiaryAccountNumber;

    @Column(name = "bank_code", length = 34)
    private String bankCode;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(length = 140)
    private String reference;

    /** Null until processing; then one of VALID/INVALID/APPROVED/RELEASED/REJECTED. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BulkLineStatus status;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BulkFileLine() {
    }

    public BulkFileLine(UUID id, BulkFile bulkFile, int lineNumber, String rawContent) {
        setId(id);
        this.bulkFile = bulkFile;
        this.lineNumber = lineNumber;
        this.rawContent = rawContent;
        this.createdAt = Instant.now();
    }

    public BulkFile getBulkFile() { return bulkFile; }
    public void setBulkFile(BulkFile bulkFile) { this.bulkFile = bulkFile; }
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }
    public String getDebitAccountNumber() { return debitAccountNumber; }
    public void setDebitAccountNumber(String debitAccountNumber) { this.debitAccountNumber = debitAccountNumber; }
    public String getBeneficiaryAccountNumber() { return beneficiaryAccountNumber; }
    public void setBeneficiaryAccountNumber(String beneficiaryAccountNumber) { this.beneficiaryAccountNumber = beneficiaryAccountNumber; }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public BulkLineStatus getStatus() { return status; }
    public void setStatus(BulkLineStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
