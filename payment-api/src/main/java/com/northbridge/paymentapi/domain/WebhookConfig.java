package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Company-level webhook (outbound event notification) configuration. Delivery is simulated locally. */
@Entity
@Table(name = "webhook_config",
        uniqueConstraints = @UniqueConstraint(name = "uq_webhook_company", columnNames = "company_id"))
public class WebhookConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 500)
    private String url;

    /** Shared secret for signing outbound payloads (mock integration; displayed masked). */
    @Column(length = 200)
    private String secret;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookConfig() {
    }

    public WebhookConfig(UUID id, Company company, String url, String secret, boolean active) {
        setId(id);
        this.company = company;
        this.url = url;
        this.secret = secret;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
