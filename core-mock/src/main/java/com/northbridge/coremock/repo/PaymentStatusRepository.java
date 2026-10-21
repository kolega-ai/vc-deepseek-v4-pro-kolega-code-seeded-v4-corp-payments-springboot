package com.northbridge.coremock.repo;

import com.northbridge.coremock.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentStatusRepository extends JpaRepository<PaymentStatus, String> {

    Optional<PaymentStatus> findByCoreRef(String coreRef);
}
