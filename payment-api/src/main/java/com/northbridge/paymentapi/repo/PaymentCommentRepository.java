package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.PaymentComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentCommentRepository extends JpaRepository<PaymentComment, UUID> {
    @EntityGraph(attributePaths = {"author"})
    List<PaymentComment> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
