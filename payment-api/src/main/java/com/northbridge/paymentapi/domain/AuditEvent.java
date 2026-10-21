package com.northbridge.paymentapi.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit event. Every state transition and every privileged action writes one event
 * carrying actor, action, before/after values, timestamp and the authorization basis.
 * No role may modify or delete audit records; a database trigger additionally rejects
 * UPDATE/DELETE on this table (see V2__audit_append_only.sql).
 */
@Entity
@Table(name = "audit_event",
        indexes = {
                @Index(name = "ix_audit_resource", columnList = "resource_type, resource_id"),
                @Index(name = "ix_audit_event_at", columnList = "event_at")
        })
public class AuditEvent extends BaseEntity {

    public enum ActorType { USER, SYSTEM }

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8)
    private ActorType actorType;

    @Column(name = "actor_id", length = 36)
    private UUID actorId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", length = 36)
    private UUID resourceId;

    @Column(name = "resource_ref", length = 40)
    private String resourceRef;

    /** Company the event belongs to, when applicable; used to scope company audit views. */
    @Column(name = "company_id", length = 36)
    private UUID companyId;

    @Column(length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @Column(name = "auth_basis", length = 500)
    private String authBasis;

    @Column(name = "request_id", length = 64)
    private String requestId;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, Instant eventAt, ActorType actorType, UUID actorId, String actorName,
                      String actorRole, String action, String resourceType, UUID resourceId,
                      String resourceRef, UUID companyId, String summary, Map<String, Object> beforeState,
                      Map<String, Object> afterState, String authBasis, String requestId) {
        setId(id);
        this.eventAt = eventAt;
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.resourceRef = resourceRef;
        this.companyId = companyId;
        this.summary = summary;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.authBasis = authBasis;
        this.requestId = requestId;
    }

    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }
    public ActorType getActorType() { return actorType; }
    public void setActorType(ActorType actorType) { this.actorType = actorType; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
    public String getResourceRef() { return resourceRef; }
    public void setResourceRef(String resourceRef) { this.resourceRef = resourceRef; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Map<String, Object> getBeforeState() { return beforeState; }
    public void setBeforeState(Map<String, Object> beforeState) { this.beforeState = beforeState; }
    public Map<String, Object> getAfterState() { return afterState; }
    public void setAfterState(Map<String, Object> afterState) { this.afterState = afterState; }
    public String getAuthBasis() { return authBasis; }
    public void setAuthBasis(String authBasis) { this.authBasis = authBasis; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
