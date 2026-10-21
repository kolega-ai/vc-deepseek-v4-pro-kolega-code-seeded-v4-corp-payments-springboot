package com.northbridge.paymentapi.repo;

import com.northbridge.paymentapi.domain.CompanyUser;
import com.northbridge.paymentapi.domain.Role;
import com.northbridge.paymentapi.domain.UserStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {

    Optional<CompanyUser> findByEmailIgnoreCase(String email);

    /** Eager variant used by the auth filter: loads company + assigned accounts without a session. */
    @Query("""
            select distinct u from CompanyUser u
            left join fetch u.company c
            left join fetch u.assignedAccounts a
            where u.id = :id
            """)
    Optional<CompanyUser> findWithAccountsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"assignedAccounts"})
    List<CompanyUser> findByCompanyIdOrderByFullNameAsc(UUID companyId);

    List<CompanyUser> findByCompanyIdAndRoleAndStatus(UUID companyId, Role role, UserStatus status);

    boolean existsByCompanyIdAndEmailIgnoreCase(UUID companyId, String email);

    @Query("""
            select u from CompanyUser u
            where u.company.id = :companyId and u.role = :role and u.status = :status
              and u.approvalLimit >= :amount
            order by u.approvalLimit desc
            """)
    List<CompanyUser> findCheckersWithLimitAtLeast(@Param("companyId") UUID companyId,
                                                   @Param("role") Role role,
                                                   @Param("status") UserStatus status,
                                                   @Param("amount") BigDecimal amount);
}
