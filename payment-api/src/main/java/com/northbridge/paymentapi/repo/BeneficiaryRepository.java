package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Beneficiary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    @EntityGraph(attributePaths = {"createdBy", "lastAmendedBy"})
    List<Beneficiary> findByCompanyIdOrderByNameAsc(UUID companyId);

    Optional<Beneficiary> findByCompanyIdAndAccountNumberAndBankCode(UUID companyId, String accountNumber, String bankCode);

    @Query("select coalesce(max(b.lastAmendedAt), max(b.createdAt)) from Beneficiary b where b.company.id = :companyId")
    Instant maxUpdatedAtForCompany(@Param("companyId") UUID companyId);

    List<Beneficiary> findByStatusAndCoolingUntilBefore(
            com.northbridge.paymentapi.domain.BeneficiaryStatus status, Instant now);
}
