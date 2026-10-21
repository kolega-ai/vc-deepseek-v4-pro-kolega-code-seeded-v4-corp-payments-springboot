package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.FxRateRow;
import com.northbridge.paymentapi.repo.FxRateRepository;
import com.northbridge.paymentapi.security.ApiException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Server-side FX conversion from the seeded FX rate table. The effective rate at the payment's
 * creation time is stored on the payment and never accepted from a client.
 */
@Service
public class FxService {

    private final FxRateRepository repository;

    public FxService(FxRateRepository repository) {
        this.repository = repository;
    }

    public record Conversion(BigDecimal fxRate, BigDecimal settlementAmount, String settlementCurrency) {
    }

    /**
     * Computes the settlement conversion for a payment. Same-currency: fxRate=1, settlement=amount.
     * Otherwise the latest effective rate for BASE/QUOTE (e.g. GBP/EUR) at the given instant is used.
     */
    public Conversion convert(String debitCurrency, String beneficiaryCurrency, BigDecimal amount, Instant at) {
        if (debitCurrency.equalsIgnoreCase(beneficiaryCurrency)) {
            return new Conversion(BigDecimal.ONE, MoneyUtil.round(amount, beneficiaryCurrency), beneficiaryCurrency);
        }
        String pair = debitCurrency.toUpperCase() + "/" + beneficiaryCurrency.toUpperCase();
        FxRateRow rate = repository.latestEffective(pair, at)
                .orElseThrow(() -> new ApiException(ApiException.Code.INTERNAL_ERROR,
                        "No FX rate published for " + pair + " as of " + at));
        BigDecimal settlement = amount.multiply(rate.getRate())
                .setScale(MoneyUtil.scale(beneficiaryCurrency), RoundingMode.HALF_UP);
        return new Conversion(rate.getRate(), settlement, beneficiaryCurrency);
    }

    /** Converts a settlement amount back to the base currency at a stored fx rate. */
    public BigDecimal toBaseCurrency(BigDecimal settlementAmount, BigDecimal fxRate, String baseCurrency) {
        if (fxRate == null || fxRate.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Invalid stored fx rate");
        }
        return settlementAmount.divide(fxRate, MoneyUtil.scale(baseCurrency), RoundingMode.HALF_UP);
    }
}
