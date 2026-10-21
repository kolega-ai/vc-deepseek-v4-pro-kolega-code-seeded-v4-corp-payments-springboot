package com.northbridge.coremock.security;

import com.northbridge.coremock.web.ApiException;

import java.util.List;
import java.util.UUID;

/**
 * A verified scope token minted by the payment-api decision tier.
 *
 * <p>core-mock only ever receives these tokens; it never mints them. The scope
 * carried in the token is the SOLE authorization basis for every business
 * endpoint: account numbers and company identifiers from request bodies, paths,
 * or query parameters are treated as untrusted input and are always checked
 * against this scope. Requests whose scope does not cover the target account
 * are rejected with 403 {@code CORE_SCOPE_DENIED}.
 *
 * @param subject   the {@code sub} claim (the acting subject in payment-api)
 * @param issuedAt  the {@code iat} claim, epoch seconds
 * @param expiresAt the {@code exp} claim, epoch seconds
 * @param scope     the parsed scope object
 */
public record ScopeToken(String subject, long issuedAt, long expiresAt, Scope scope) {

    /**
     * Scope object carried in the token payload. Either a company scope
     * (type {@code company} with a companyId and a list of permitted account
     * numbers) or a bank scope (type {@code bank} with op {@code postings}).
     */
    public record Scope(String type, UUID companyId, List<String> accounts, String operation) {

        public boolean isCompany() {
            return "company".equals(type);
        }

        public boolean isBank() {
            return "bank".equals(type);
        }

        public static Scope company(UUID companyId, List<String> accounts) {
            return new Scope("company", companyId, List.copyOf(accounts), null);
        }

        public static Scope bank() {
            return new Scope("bank", null, null, "postings");
        }
    }

    public boolean isBankScope() {
        return scope.isBank();
    }

    /**
     * Asserts that this token permits operations on the given account.
     * Bank scopes permit all accounts; company scopes permit only the account
     * numbers explicitly listed at minting time.
     *
     * @throws ApiException 403 CORE_SCOPE_DENIED when the account is not permitted
     */
    public void requireAccountAccess(String accountNumber) {
        if (scope.isBank()) {
            return;
        }
        if (!scope.isCompany() || scope.accounts() == null || !scope.accounts().contains(accountNumber)) {
            throw ApiException.scopeDenied("Token scope does not permit access to this account");
        }
    }

    /**
     * Asserts that this token was minted for the given company. Bank scopes
     * bypass the check; company scopes must carry exactly this companyId.
     *
     * @throws ApiException 403 CORE_SCOPE_DENIED on mismatch
     */
    public void requireCompany(UUID companyId) {
        if (scope.isBank()) {
            return;
        }
        if (!scope.isCompany() || !companyId.equals(scope.companyId())) {
            throw ApiException.scopeDenied("Token scope does not permit access to this company");
        }
    }

    /**
     * Asserts that this token carries a bank scope ({@code {"type":"bank","op":"postings"}}).
     *
     * @throws ApiException 403 CORE_SCOPE_DENIED otherwise
     */
    public void requireBankScope() {
        if (!scope.isBank()) {
            throw ApiException.scopeDenied("This endpoint requires a bank scope token");
        }
    }
}
