package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.ApprovalAction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {
    @EntityGraph(attributePaths = {"actor"})
    List<ApprovalAction> findByPaymentIdOrderByActedAtAsc(UUID paymentId);

    long countByPaymentIdAndDecision(UUID paymentId, ApprovalAction.Decision decision);
}
