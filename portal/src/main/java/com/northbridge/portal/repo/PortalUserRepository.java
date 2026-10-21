package com.northbridge.portal.repo;

import com.northbridge.portal.domain.PortalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalUserRepository extends JpaRepository<PortalUser, UUID> {
    Optional<PortalUser> findByEmailIgnoreCase(String email);
    List<PortalUser> findByCompanyIdOrderByFullNameAsc(UUID companyId);
    List<PortalUser> findByStatus(String status);
    boolean existsByEmailIgnoreCase(String email);
}
