package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.Account;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.AccessControlService;
import com.northbridge.paymentapi.service.CoreClient;
import com.northbridge.paymentapi.web.dto.CompanyDtos;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Company accounts. A user restricted to specific accounts only ever sees (and can only debit)
 * those accounts; unrestricted users see every account of their company. Balances come live from
 * the vendor core under a company-scoped token.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccessControlService access;
    private final CoreClient coreClient;
    private final com.northbridge.paymentapi.repo.AccountRepository accountRepository;

    public AccountController(AccessControlService access,
                             CoreClient coreClient,
                             com.northbridge.paymentapi.repo.AccountRepository accountRepository) {
        this.access = access;
        this.coreClient = coreClient;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<CompanyDtos.AccountView> list() {
        AuthPrincipal actor = AuthSupport.require();
        if (!actor.isCompanyUser()) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Company scope required");
        }
        List<Account> accounts = accountRepository.findByCompanyIdOrderByAccountNumberAsc(actor.companyId());
        return accounts.stream()
                .filter(a -> actor.mayUseAccount(a.getId()))
                .map(a -> view(a, actor))
                .toList();
    }

    @GetMapping("/{id}")
    public CompanyDtos.AccountView get(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        Account account = access.requireUsableAccount(actor, id);
        return view(account, actor);
    }

    private CompanyDtos.AccountView view(Account account, AuthPrincipal actor) {
        BigDecimal balance = null;
        try {
            CoreClient.Balance coreBalance = coreClient.balance(account.getAccountNumber(),
                    account.getCompany().getId());
            balance = coreBalance.balance();
        } catch (Exception e) {
            balance = null; // core unreachable: balance not shown, account metadata still available
        }
        return new CompanyDtos.AccountView(account.getId(), account.getAccountNumber(),
                account.getCurrency(), account.getProduct(), account.getName(), balance,
                account.getCompany().getId());
    }
}
