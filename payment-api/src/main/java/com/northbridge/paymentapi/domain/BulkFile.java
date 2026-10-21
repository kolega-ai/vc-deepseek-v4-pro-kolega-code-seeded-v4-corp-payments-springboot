package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bulk_file",
        uniqueConstraints = @UniqueConstraint(name = "uq_bulk_file_ref", columnNames = "ref"))
public class BulkFile extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String ref;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BulkFileStatus status;

    /** Declared total from the file header. */
    @Column(name = "declared_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal declaredTotal;

    /** Declared line count from the file header. */
    @Column(name = "declared_count", nullable = false)
    private int declaredCount;

    @Column(name = "parsed_count", nullable = false)
    private int parsedCount;

    @Column(name = "valid_count", nullable = false)
    private int validCount;

    @Column(name = "invalid_count", nullable = false)
    private int invalidCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private CompanyUser uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @OneToMany(mappedBy = "bulkFile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<BulkFileLine> lines = new ArrayList<>();

    protected BulkFile() {
    }

    public BulkFile(UUID id, String ref, Company company, String fileName, BigDecimal declaredTotal,
                    int declaredCount, CompanyUser uploadedBy) {
        setId(id);
        this.ref = ref;
        this.company = company;
        this.fileName = fileName;
        this.status = BulkFileStatus.UPLOADED;
        this.declaredTotal = declaredTotal;
        this.declaredCount = declaredCount;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public BulkFileStatus getStatus() { return status; }
    public void setStatus(BulkFileStatus status) { this.status = status; }
    public BigDecimal getDeclaredTotal() { return declaredTotal; }
    public void setDeclaredTotal(BigDecimal declaredTotal) { this.declaredTotal = declaredTotal; }
    public int getDeclaredCount() { return declaredCount; }
    public void setDeclaredCount(int declaredCount) { this.declaredCount = declaredCount; }
    public int getParsedCount() { return parsedCount; }
    public void setParsedCount(int parsedCount) { this.parsedCount = parsedCount; }
    public int getValidCount() { return validCount; }
    public void setValidCount(int validCount) { this.validCount = validCount; }
    public int getInvalidCount() { return invalidCount; }
    public void setInvalidCount(int invalidCount) { this.invalidCount = invalidCount; }
    public CompanyUser getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(CompanyUser uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public List<BulkFileLine> getLines() { return lines; }
    public void setLines(List<BulkFileLine> lines) { this.lines = lines; }
}
