package com.northbridge.portal.repo;

import com.northbridge.portal.domain.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    List<EmailOutbox> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
