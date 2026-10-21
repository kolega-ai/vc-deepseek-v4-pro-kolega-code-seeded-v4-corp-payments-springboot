package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Shared ownership/scope checks used by every payment-detail path.
 *
 * <p>PRECISION LOCATION #1: the payment-detail ownership check lives here, in a shared service one
 * call frame ABOVE the controller method (controllers call {@link PaymentQueryService}, which calls
 * {@link #requirePaymentAccess}), rather than inline in the controller. Company users can only reach
 * their own company's records; foreign resources are reported as NOT_FOUND so existence is never
 * leaked.</p>
 */
@Service
public class AccessControlService {

    private final PaymentRepository paymentRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final BulkFileRepository bulkFileRepository;
    private final CompanyUserRepository userRepository;

    public AccessControlService(PaymentRepository paymentRepository,
                                BeneficiaryRepository beneficiaryRepository,
                                AccountRepository accountRepository,
                                BulkFileRepository bulkFileRepository,
                                CompanyUserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.accountRepository = accountRepository;
        this.bulkFileRepository = bulkFileRepository;
        this.userRepository = userRepository;
    }

    /** Bank users reach everything; company users only their own company's records. */
    public boolean inScope(AuthPrincipal principal, UUID companyId) {
        if (principal.isBankUser()) {
            return true;
        }
        return principal.companyId() != null && principal.companyId().equals(companyId);
    }

    public Payment requirePaymentAccess(AuthPrincipal principal, UUID paymentId) {
        Payment payment = paymentRepository.findDetailById(paymentId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Payment not found"));
        if (!inScope(principal, payment.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "Payment not found");
        }
        return payment;
    }

    public Beneficiary requireBeneficiaryAccess(AuthPrincipal principal, UUID beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found"));
        if (!inScope(principal, beneficiary.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found");
        }
        return beneficiary;
    }

    public Account requireAccountAccess(AuthPrincipal principal, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Account not found"));
        if (!inScope(principal, account.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "Account not found");
        }
        return account;
    }

    public BulkFile requireBulkFileAccess(AuthPrincipal principal, UUID bulkFileId) {
        BulkFile file = bulkFileRepository.findById(bulkFileId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Bulk file not found"));
        if (!inScope(principal, file.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "Bulk file not found");
        }
        return file;
    }

    public CompanyUser requireCompanyUserAccess(AuthPrincipal principal, UUID userId) {
        CompanyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "User not found"));
        if (!inScope(principal, user.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "User not found");
        }
        return user;
    }

    /**
     * Account restriction: a user restricted to specific accounts cannot view or debit unassigned
     * accounts of the same company.
     */
    public Account requireUsableAccount(AuthPrincipal principal, UUID accountId) {
        Account account = requireAccountAccess(principal, accountId);
        if (principal.isCompanyUser() && !principal.mayUseAccount(account.getId())) {
            throw new ApiException(ApiException.Code.ACCOUNT_NOT_ASSIGNED,
                    "This account is not assigned to you");
        }
        return account;
    }

    /** Company must be active for any money-moving or data change. */
    public void requireActiveCompany(Company company) {
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new ApiException(ApiException.Code.COMPANY_INACTIVE,
                    "Company " + company.getLegalName() + " is not active");
        }
    }
}
