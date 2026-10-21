package com.northbridge.portal.repo;

import com.northbridge.portal.domain.PortalAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PortalAuditEventRepository extends JpaRepository<PortalAuditEvent, UUID> {
    Page<PortalAuditEvent> findAllByOrderByEventAtDesc(Pageable pageable);
    Page<PortalAuditEvent> findByCompanyIdOrderByEventAtDesc(UUID companyId, Pageable pageable);
    Page<PortalAuditEvent> findByEventAtBetweenOrderByEventAtDesc(Instant from, Instant to, Pageable pageable);
    List<PortalAuditEvent> findTop10ByCompanyIdOrderByEventAtDesc(UUID companyId);
}
