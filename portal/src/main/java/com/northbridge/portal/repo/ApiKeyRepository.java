package com.northbridge.portal.repo;

import com.northbridge.portal.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Optional<ApiKey> findByKeyHash(String keyHash);
}
