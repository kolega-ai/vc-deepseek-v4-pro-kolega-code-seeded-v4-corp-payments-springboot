package com.northbridge.coremock.service;

import com.northbridge.coremock.domain.CoreAccount;
import com.northbridge.coremock.domain.CorePosting;
import com.northbridge.coremock.repo.CoreAccountRepository;
import com.northbridge.coremock.repo.CorePostingRepository;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.web.ApiException;
import com.northbridge.coremock.web.dto.BalanceResponse;
import com.northbridge.coremock.web.dto.CreateAccountRequest;
import com.northbridge.coremock.web.dto.InternalPostingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Account queries and the bank-only account creation / reconciliation surface.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final String ACCOUNT_NUMBER_PATTERN = "[0-9A-Z]{4,20}";

    private final CoreAccountRepository accountRepository;
    private final CorePostingRepository postingRepository;

    public AccountService(CoreAccountRepository accountRepository, CorePostingRepository postingRepository) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(ScopeToken scopeToken, String accountNumber) {
        // Scope first: an out-of-scope account is 403 regardless of whether it exists.
        scopeToken.requireAccountAccess(accountNumber);
        CoreAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(ApiException::accountNotFound);
        scopeToken.requireCompany(account.getCompanyId());
        return BalanceResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<InternalPostingResponse> reconcilePostings(ScopeToken scopeToken, String accountNumber, String sinceRaw) {
        scopeToken.requireBankScope();
        if (accountNumber != null && accountNumber.length() > 20) {
            throw ApiException.validation("accountNumber must be at most 20 characters");
        }
        Instant since = null;
        if (sinceRaw != null && !sinceRaw.isBlank()) {
            try {
                since = Instant.parse(sinceRaw);
            } catch (DateTimeParseException e) {
                throw ApiException.validation("since must be an ISO-8601 instant, e.g. 2026-09-04T09:00:00Z");
            }
        }
        List<CorePosting> postings;
        if (accountNumber == null || accountNumber.isBlank()) {
            postings = since == null
                    ? postingRepository.findAllByOrderByPostedAtDesc()
                    : postingRepository.findByPostedAtGreaterThanEqualOrderByPostedAtDesc(since);
        } else {
            postings = since == null
                    ? postingRepository.findByAccountNumberOrderByPostedAtDesc(accountNumber)
                    : postingRepository.findByAccountNumberAndPostedAtGreaterThanEqualOrderByPostedAtDesc(accountNumber, since);
        }
        return postings.stream().map(InternalPostingResponse::from).toList();
    }

    @Transactional
    public BalanceResponse createAccount(ScopeToken scopeToken, CreateAccountRequest request) {
        scopeToken.requireBankScope();

        UUID companyId;
        try {
            companyId = UUID.fromString(request.companyId());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("companyId must be a valid UUID");
        }

        String accountNumber = request.accountNumber().trim();
        if (!accountNumber.matches(ACCOUNT_NUMBER_PATTERN)) {
            throw ApiException.validation("accountNumber must be 4-20 uppercase alphanumeric characters");
        }
        if (!request.currency().matches("[A-Z]{3}")) {
            throw ApiException.validation("currency must be 3 uppercase letters");
        }
        BigDecimal openingBalance = request.openingBalance();
        if (openingBalance.signum() < 0) {
            throw ApiException.validation("openingBalance must be >= 0");
        }
        if (openingBalance.scale() > 4) {
            throw ApiException.validation("openingBalance must not exceed 4 decimal places");
        }
        if (request.product() != null && !request.product().isBlank() && request.product().trim().length() > 32) {
            throw ApiException.validation("product must be at most 32 characters");
        }
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw ApiException.accountExists(accountNumber);
        }

        CoreAccount account = new CoreAccount();
        account.setAccountNumber(accountNumber);
        account.setCompanyId(companyId);
        account.setCurrency(request.currency());
        account.setProduct(request.product() == null || request.product().isBlank() ? null : request.product().trim());
        account.setBalance(openingBalance);
        account.setCreatedAt(Instant.now());
        accountRepository.save(account);

        log.info("core account {} created for company {}", accountNumber, companyId);
        return BalanceResponse.from(account);
    }
}
