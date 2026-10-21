package com.northbridge.coremock.web;

import com.northbridge.coremock.security.ScopeAuthInterceptor;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.service.AccountService;
import com.northbridge.coremock.web.dto.BalanceResponse;
import com.northbridge.coremock.web.dto.CreateAccountRequest;
import com.northbridge.coremock.web.dto.InternalPostingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bank-only internal surface used by the payment-api tier and the
 * reconciliation batch: posting lists per account and account creation.
 * Every route requires a bank scope token ({@code {"type":"bank","op":"postings"}}).
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final AccountService accountService;

    public InternalController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/postings")
    public List<InternalPostingResponse> postings(
            @RequestAttribute(ScopeAuthInterceptor.SCOPE_TOKEN_ATTRIBUTE) ScopeToken scopeToken,
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) String since) {
        return accountService.reconcilePostings(scopeToken, accountNumber, since);
    }

    @PostMapping("/accounts")
    public BalanceResponse createAccount(
            @RequestAttribute(ScopeAuthInterceptor.SCOPE_TOKEN_ATTRIBUTE) ScopeToken scopeToken,
            @Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(scopeToken, request);
    }
}
