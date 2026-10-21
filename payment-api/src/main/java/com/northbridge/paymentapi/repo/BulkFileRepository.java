package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.BulkFile;
import com.northbridge.paymentapi.domain.BulkFileStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BulkFileRepository extends JpaRepository<BulkFile, UUID> {

    List<BulkFile> findTop20ByStatusOrderByUploadedAtAsc(BulkFileStatus status);

    List<BulkFile> findByCompanyIdOrderByUploadedAtDesc(UUID companyId);

    Optional<BulkFile> findByRef(String ref);

    @EntityGraph(attributePaths = {"uploadedBy"})
    @Query("select f from BulkFile f where f.company.id = :companyId order by f.uploadedAt desc")
    List<BulkFile> findAllForCompany(@Param("companyId") UUID companyId);
}
