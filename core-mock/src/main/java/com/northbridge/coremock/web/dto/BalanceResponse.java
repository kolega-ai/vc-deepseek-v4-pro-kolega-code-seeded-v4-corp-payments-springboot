package com.northbridge.coremock.web.dto;

import com.northbridge.coremock.domain.CoreAccount;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Account balance view. Also used as the response for account creation on the
 * internal API, where {@code balance} carries the opening balance.
 */
public record BalanceResponse(String accountNumber, String currency, BigDecimal balance, UUID companyId) {

    public static BalanceResponse from(CoreAccount account) {
        return new BalanceResponse(account.getAccountNumber(), account.getCurrency(),
                account.getBalance(), account.getCompanyId());
    }
}
