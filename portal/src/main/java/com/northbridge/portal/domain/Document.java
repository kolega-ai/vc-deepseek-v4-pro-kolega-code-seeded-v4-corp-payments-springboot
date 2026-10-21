package com.northbridge.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Uploaded file metadata; bytes live on local disk under the canonical uploads directory. */
@Entity
@Table(name = "document")
public class Document {

    public enum Category {
        PAYMENT_SUPPORT, BULK_ERROR_REPORT, IDENTITY_VERIFICATION
    }

    @Id
    @Column(nullable = false, length = 36)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "uploader_id")
    private UUID uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Random storage name; the original file name is never used on disk. */
    @Column(name = "stored_name", nullable = false, length = 80, unique = true)
    private String storedName;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "bulk_file_id")
    private UUID bulkFileId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Document() {
    }

    public Document(UUID id, UUID companyId, UUID uploaderId, Category category, String fileName,
                    String storedName, String mimeType, long sizeBytes, UUID paymentId, UUID bulkFileId) {
        this.id = id;
        this.companyId = companyId;
        this.uploaderId = uploaderId;
        this.category = category;
        this.fileName = fileName;
        this.storedName = storedName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.paymentId = paymentId;
        this.bulkFileId = bulkFileId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public UUID getUploaderId() { return uploaderId; }
    public void setUploaderId(UUID uploaderId) { this.uploaderId = uploaderId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public UUID getBulkFileId() { return bulkFileId; }
    public void setBulkFileId(UUID bulkFileId) { this.bulkFileId = bulkFileId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
