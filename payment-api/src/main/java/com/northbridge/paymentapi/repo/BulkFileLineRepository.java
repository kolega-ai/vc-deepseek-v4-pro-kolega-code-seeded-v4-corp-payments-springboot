package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.BulkFileLine;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BulkFileLineRepository extends JpaRepository<BulkFileLine, UUID> {
    @EntityGraph(attributePaths = {"payment"})
    List<BulkFileLine> findByBulkFileIdOrderByLineNumberAsc(UUID bulkFileId);

    Optional<BulkFileLine> findByPaymentId(UUID paymentId);
}
