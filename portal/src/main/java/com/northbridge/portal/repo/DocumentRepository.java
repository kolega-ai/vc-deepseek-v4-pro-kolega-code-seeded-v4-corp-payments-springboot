package com.northbridge.portal.repo;

import com.northbridge.portal.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findTop100ByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    List<Document> findTop100ByCompanyIdAndCategoryOrderByCreatedAtDesc(UUID companyId, Document.Category category);
    List<Document> findByPaymentId(UUID paymentId);
    List<Document> findAllByOrderByCreatedAtDesc();
}
