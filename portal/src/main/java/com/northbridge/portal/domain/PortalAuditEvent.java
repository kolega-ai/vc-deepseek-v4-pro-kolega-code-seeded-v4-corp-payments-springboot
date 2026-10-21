package com.northbridge.portal.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Portal-local append-only audit event (auth events, documents, API keys). */
@Entity
@Table(name = "portal_audit_event")
public class PortalAuditEvent {

    @Id
    @Column(nullable = false, length = 36)
    private UUID id;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_email", length = 254)
    private String actorEmail;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_ref", length = 120)
    private String resourceRef;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(length = 500)
    private String summary;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    protected PortalAuditEvent() {
    }

    public PortalAuditEvent(UUID id, UUID actorId, String actorEmail, String actorRole, String action,
                            String resourceType, String resourceRef, UUID companyId, String summary,
                            String ipAddress) {
        this.id = id;
        this.eventAt = Instant.now();
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceRef = resourceRef;
        this.companyId = companyId;
        this.summary = summary;
        this.ipAddress = ipAddress;
    }

    public UUID getId() { return id; }
    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }
    public UUID getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceRef() { return resourceRef; }
    public UUID getCompanyId() { return companyId; }
    public String getSummary() { return summary; }
    public String getIpAddress() { return ipAddress; }
}
