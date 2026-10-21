package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    /** Fetch-joined search used by list/dashboard mapping (single collection, no bag conflict). */
    @Override
    @EntityGraph(attributePaths = {"beneficiary", "debitAccount", "createdBy", "amendedBy",
            "bulkFile", "amendments"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    /** Fetch-joined detail lookup used by the read model. */
    @EntityGraph(attributePaths = {"beneficiary", "debitAccount", "createdBy", "amendedBy",
            "bulkFile", "company"})
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findDetailById(@Param("id") UUID id);

    Optional<Payment> findByRef(String ref);

    Optional<Payment> findByRefAndCompanyId(String ref, UUID companyId);

    /**
     * Pessimistic row lock on the payment used by release/retry so a duplicate release attempt is
     * rejected and a payment is never posted twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findForUpdateById(@Param("id") UUID id);

    @Query("""
            select p from Payment p
            where p.company.id = :companyId and p.status in :statuses and p.valueDate = :valueDate
            """)
    List<Payment> findForUtilisation(@Param("companyId") UUID companyId,
                                     @Param("statuses") List<PaymentStatus> statuses,
                                     @Param("valueDate") LocalDate valueDate);

    @Query("""
            select p from Payment p
            where p.status = :status and p.valueDate <= :valueDate
            order by p.createdAt asc
            """)
    List<Payment> findDueForRelease(@Param("status") PaymentStatus status,
                                    @Param("valueDate") LocalDate valueDate);

    List<Payment> findByCompanyIdAndStatusIn(UUID companyId, List<PaymentStatus> statuses);

    long countByCompanyIdAndStatusIn(UUID companyId, List<PaymentStatus> statuses);

    long countByStatusIn(List<PaymentStatus> statuses);


    @EntityGraph(attributePaths = {"beneficiary", "debitAccount", "createdBy", "bulkFile", "amendments"})
    List<Payment> findTop10ByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    @EntityGraph(attributePaths = {"beneficiary", "debitAccount", "createdBy", "bulkFile", "amendments"})
    List<Payment> findByStatusIn(List<PaymentStatus> statuses);
}
