package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    List<Notification> findTop50ByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId);
}
