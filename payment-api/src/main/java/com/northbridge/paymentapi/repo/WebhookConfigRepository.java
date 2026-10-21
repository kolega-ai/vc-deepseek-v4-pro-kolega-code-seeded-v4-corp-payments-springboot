package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.WebhookConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {
    Optional<WebhookConfig> findByCompanyId(UUID companyId);
}
