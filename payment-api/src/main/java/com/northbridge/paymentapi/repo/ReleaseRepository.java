package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Release;
import com.northbridge.paymentapi.domain.ReleaseInvestigationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseRepository extends JpaRepository<Release, UUID>, JpaSpecificationExecutor<Release> {

    @Override
    @EntityGraph(attributePaths = {"payment", "payment.debitAccount"})
    Page<Release> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"payment", "payment.debitAccount"})
    java.util.List<Release> findAll();

    @EntityGraph(attributePaths = {"payment", "payment.debitAccount"})
    Optional<Release> findByPaymentId(UUID paymentId);

    List<Release> findByInvestigationStatusIn(List<ReleaseInvestigationStatus> statuses);

    long countByInvestigationStatusIn(List<ReleaseInvestigationStatus> statuses);
}
