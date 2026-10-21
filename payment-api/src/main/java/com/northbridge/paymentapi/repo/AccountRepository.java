package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByCompanyIdOrderByAccountNumberAsc(UUID companyId);

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Pessimistic row lock on the debit account. Release holds this lock across the
     * read-check-write against the core system so that two concurrent releases for the same
     * account are serialised and the balance re-check cannot race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findForUpdateById(@Param("id") UUID id);
}
