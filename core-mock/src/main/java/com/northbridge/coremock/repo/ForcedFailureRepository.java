package com.northbridge.coremock.repo;

import com.northbridge.coremock.domain.ForcedFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ForcedFailureRepository extends JpaRepository<ForcedFailure, String> {

    Optional<ForcedFailure> findByRequestRef(String requestRef);
}
