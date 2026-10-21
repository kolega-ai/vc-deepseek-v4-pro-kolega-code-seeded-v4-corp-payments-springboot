package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Reversal;
import com.northbridge.paymentapi.domain.ReversalStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReversalRepository extends JpaRepository<Reversal, UUID> {

    @EntityGraph(attributePaths = {"payment", "requestedBy", "approvedBy"})
    List<Reversal> findByStatusOrderByRequestedAtAsc(ReversalStatus status);

    @EntityGraph(attributePaths = {"payment", "requestedBy", "approvedBy"})
    List<Reversal> findAllByOrderByRequestedAtDesc();

    Optional<Reversal> findByPaymentId(UUID paymentId);

    @EntityGraph(attributePaths = {"payment", "requestedBy", "approvedBy"})
    List<Reversal> findTop20ByOrderByRequestedAtDesc();
}
