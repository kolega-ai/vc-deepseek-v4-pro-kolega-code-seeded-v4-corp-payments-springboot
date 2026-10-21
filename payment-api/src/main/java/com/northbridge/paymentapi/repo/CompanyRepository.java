package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
}
