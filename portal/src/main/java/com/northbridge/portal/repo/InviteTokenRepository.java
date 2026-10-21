package com.northbridge.portal.repo;

import com.northbridge.portal.domain.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InviteTokenRepository extends JpaRepository<InviteToken, UUID> {
    Optional<InviteToken> findByTokenHash(String tokenHash);
}
