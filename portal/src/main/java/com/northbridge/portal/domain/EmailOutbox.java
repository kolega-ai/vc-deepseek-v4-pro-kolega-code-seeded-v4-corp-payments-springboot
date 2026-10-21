package com.northbridge.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound email queue. Email is a LOCAL MOCK: messages are written to this outbox table and a
 * scheduled job marks them sent — no SMTP/SaaS dependency. Use this instead of sending directly.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    @Id
    @Column(nullable = false, length = 36)
    private UUID id;

    @Column(name = "to_email", nullable = false, length = 254)
    private String toEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailOutbox() {
    }

    public EmailOutbox(UUID id, String toEmail, String subject, String body) {
        this.id = id;
        this.toEmail = toEmail;
        this.subject = subject;
        this.body = body;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
