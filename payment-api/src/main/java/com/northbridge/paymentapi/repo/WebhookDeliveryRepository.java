package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"payment"})
    List<WebhookDelivery> findTop100ByStatusOrderByCreatedAtAsc(String status);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"payment"})
    List<WebhookDelivery> findTop20ByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
