package com.northbridge.coremock.repo;

import com.northbridge.coremock.domain.CoreAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CoreAccountRepository extends JpaRepository<CoreAccount, UUID> {

    Optional<CoreAccount> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Reads the account row with a pessimistic write lock ({@code SELECT ... FOR UPDATE})
     * so that balance checks and updates are atomic across concurrent postings.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CoreAccount a where a.accountNumber = :accountNumber")
    Optional<CoreAccount> findForUpdateByAccountNumber(@Param("accountNumber") String accountNumber);
}
