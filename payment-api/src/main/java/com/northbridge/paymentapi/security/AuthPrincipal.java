package com.northbridge.paymentapi.security;

import com.northbridge.paymentapi.domain.Role;

import java.util.Set;
import java.util.UUID;

/**
 * The acting principal resolved by this tier FROM ITS OWN DATABASE on every request.
 * No field here ever originates from a caller-supplied header, query, path or body value —
 * only from the signed token's subject, resolved against the local user table.
 */
public record AuthPrincipal(
        UUID userId,
        String email,
        String fullName,
        Role role,
        UUID companyId,
        java.math.BigDecimal approvalLimit,
        Set<UUID> assignedAccountIds,
        boolean service
) {
    public boolean isBankUser() {
        return role.isBankRole();
    }

    public boolean isCompanyUser() {
        return !role.isBankRole();
    }

    public boolean hasRole(Role expected) {
        return role == expected;
    }

    /** A user with no assigned accounts may use every account of their company. */
    public boolean isRestrictedToAccounts() {
        return assignedAccountIds != null && !assignedAccountIds.isEmpty();
    }

    public boolean mayUseAccount(UUID accountId) {
        if (!isRestrictedToAccounts()) {
            return true;
        }
        return assignedAccountIds.contains(accountId);
    }

    public String authBasis() {
        return "user=" + email + " role=" + role.apiName()
                + (companyId != null ? " company=" + companyId : "");
    }
}
