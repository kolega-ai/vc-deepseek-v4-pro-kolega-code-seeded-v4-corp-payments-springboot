package com.northbridge.coremock.web;

import com.northbridge.coremock.security.ScopeAuthInterceptor;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.service.AccountService;
import com.northbridge.coremock.web.dto.BalanceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /v1/accounts/{accountNumber}/balance — company scope must include the
 * account (or bank scope); otherwise 403 CORE_SCOPE_DENIED / 404 ACCOUNT_NOT_FOUND.
 */
@RestController
@RequestMapping("/v1/accounts")
public class AccountsController {

    private final AccountService accountService;

    public AccountsController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountNumber}/balance")
    public BalanceResponse balance(
            @RequestAttribute(ScopeAuthInterceptor.SCOPE_TOKEN_ATTRIBUTE) ScopeToken scopeToken,
            @PathVariable String accountNumber) {
        return accountService.balance(scopeToken, accountNumber);
    }
}
