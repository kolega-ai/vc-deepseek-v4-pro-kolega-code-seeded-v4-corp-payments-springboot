package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.AuditEvent;
import com.northbridge.paymentapi.repo.AuditEventRepository;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit service. Every state transition and every privileged action writes one event
 * carrying actor, action, before/after values, timestamp and the authorization basis. There is no
 * update or delete path anywhere in the application.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Audit by the acting user (portal-session principal); company scoped from the actor. */
    public void audit(AuthPrincipal actor, String action, String resourceType, UUID resourceId,
                      String resourceRef, String summary, Map<String, Object> before,
                      Map<String, Object> after) {
        write(new AuditEvent(
                UUID.randomUUID(), Instant.now(), AuditEvent.ActorType.USER,
                actor.userId(), actor.fullName(), actor.role() == null ? null : actor.role().apiName(),
                action, resourceType, resourceId, resourceRef, actor.companyId(), summary,
                copy(before), copy(after), actor.authBasis(), requestId()));
    }

    /** Audit by the acting user with an explicit authorization-basis override. */
    public void audit(AuthPrincipal actor, String action, String resourceType, UUID resourceId,
                      String resourceRef, String summary, Map<String, Object> before,
                      Map<String, Object> after, String basisOverride) {
        write(new AuditEvent(
                UUID.randomUUID(), Instant.now(), AuditEvent.ActorType.USER,
                actor.userId(), actor.fullName(), actor.role() == null ? null : actor.role().apiName(),
                action, resourceType, resourceId, resourceRef, actor.companyId(), summary,
                copy(before), copy(after), basisOverride, requestId()));
    }

    /** Audit by a system actor (batch release, cooling elapse, reconciliation, seeding). */
    public void auditSystem(String systemName, String action, String resourceType, UUID resourceId,
                            String resourceRef, String summary, Map<String, Object> before,
                            Map<String, Object> after, String basis) {
        write(new AuditEvent(
                UUID.randomUUID(), Instant.now(), AuditEvent.ActorType.SYSTEM,
                null, systemName, null,
                action, resourceType, resourceId, resourceRef, null, summary,
                copy(before), copy(after), basis, requestId()));
    }

    /** System audit scoped to a company, so company users can see it in their audit view. */
    public void auditSystemForCompany(String systemName, UUID companyId, String action,
                                      String resourceType, UUID resourceId, String resourceRef,
                                      String summary, Map<String, Object> before,
                                      Map<String, Object> after, String basis) {
        write(new AuditEvent(
                UUID.randomUUID(), Instant.now(), AuditEvent.ActorType.SYSTEM,
                null, systemName, null,
                action, resourceType, resourceId, resourceRef, companyId, summary,
                copy(before), copy(after), basis, requestId()));
    }

    private void write(AuditEvent event) {
        repository.save(event);
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    private static String requestId() {
        String id = MDC.get("requestId");
        return id == null ? "n/a" : id;
    }
}
