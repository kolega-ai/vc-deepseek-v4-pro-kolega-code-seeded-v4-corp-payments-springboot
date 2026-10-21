package com.northbridge.coremock.repo;

import com.northbridge.coremock.domain.CorePosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorePostingRepository extends JpaRepository<CorePosting, UUID> {

    Optional<CorePosting> findByRequestRef(String requestRef);

    long countByRequestRef(String requestRef);

    List<CorePosting> findByAccountNumberOrderByPostedAtDesc(String accountNumber);

    List<CorePosting> findByAccountNumberAndPostedAtGreaterThanEqualOrderByPostedAtDesc(String accountNumber, Instant since);

    List<CorePosting> findAllByOrderByPostedAtDesc();

    List<CorePosting> findByPostedAtGreaterThanEqualOrderByPostedAtDesc(Instant since);
}
