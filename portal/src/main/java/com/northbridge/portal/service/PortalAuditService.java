package com.northbridge.portal.service;

import com.northbridge.portal.domain.PortalAuditEvent;
import com.northbridge.portal.repo.PortalAuditEventRepository;
import com.northbridge.portal.security.AuthContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Portal-local append-only audit (auth events, documents, API keys, exports). */
@Service
public class PortalAuditService {

    private final PortalAuditEventRepository repository;
    private final AuthContext auth;

    public PortalAuditService(PortalAuditEventRepository repository, AuthContext auth) {
        this.repository = repository;
        this.auth = auth;
    }

    public void audit(String action, String resourceType, String resourceRef, String summary) {
        audit(action, resourceType, resourceRef, summary, null);
    }

    public void audit(String action, String resourceType, String resourceRef, String summary,
                      String basisOverride) {
        var user = auth.currentUser();
        repository.save(new PortalAuditEvent(UUID.randomUUID(),
                user == null ? null : user.getId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getRole(),
                action, resourceType, resourceRef,
                user == null ? null : user.getCompanyId(),
                summary, auth.clientIp()));
    }

    public void auditSystem(String actorEmail, String actorRole, UUID companyId, String action,
                            String resourceType, String resourceRef, String summary) {
        repository.save(new PortalAuditEvent(UUID.randomUUID(), null, actorEmail, actorRole, action,
                resourceType, resourceRef, companyId, summary, null));
    }
}
