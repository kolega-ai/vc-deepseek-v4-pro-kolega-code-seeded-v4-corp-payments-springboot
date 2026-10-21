package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * Append-only: this repository exposes only save/list operations. There is deliberately no
 * update/delete path in the application, and the database trigger in V2__audit_append_only.sql
 * rejects UPDATE/DELETE at the database level as well.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findTop20ByResourceIdOrderByEventAtDesc(UUID resourceId);
}
