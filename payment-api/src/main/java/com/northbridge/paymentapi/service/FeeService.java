package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.FeeScheduleRow;
import com.northbridge.paymentapi.domain.PaymentRail;
import com.northbridge.paymentapi.repo.FeeScheduleRepository;
import com.northbridge.paymentapi.security.ApiException;
import ognl.Ognl;
import ognl.OgnlException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side fee computation from the seeded fee schedule. Fee values are displayed to the user
 * before approval but are NEVER accepted from a client — a submitted fee field is ignored and the
 * value is always recomputed from this schedule.
 */
@Service
public class FeeService {

    private final FeeScheduleRepository repository;

    public FeeService(FeeScheduleRepository repository) {
        this.repository = repository;
    }

    /** Rail for a payment: same-currency is domestic; otherwise the beneficiary currency decides. */
    public static PaymentRail railFor(String debitCurrency, String beneficiaryCurrency) {
        if (debitCurrency.equalsIgnoreCase(beneficiaryCurrency)) {
            return PaymentRail.FASTER_PAYMENTS;
        }
        return switch (beneficiaryCurrency.toUpperCase()) {
            case "EUR" -> PaymentRail.SEPA;
            case "USD" -> PaymentRail.SWIFT;
            default -> PaymentRail.SWIFT;
        };
    }

    public record FeeBreakdown(BigDecimal fee, PaymentRail rail, BigDecimal flat, BigDecimal pct,
                               BigDecimal min, BigDecimal max) {
    }

    /**
     * fee = clamp(flat + amount * pct/100, min, max), rounded to the currency minor unit.
     * Uses the schedule row effective at the given instant.
     */
    public FeeBreakdown compute(PaymentRail rail, String currency, BigDecimal amount, Instant at) {
        var rows = repository.findActive(rail, currency.toUpperCase(), at);
        if (rows.isEmpty()) {
            throw new ApiException(ApiException.Code.INTERNAL_ERROR,
                    "No fee schedule row for " + rail + " in " + currency);
        }
        FeeScheduleRow row = rows.get(0);
        BigDecimal variable = amount.multiply(row.getPctFee()).divide(BigDecimal.valueOf(100),
                4, RoundingMode.HALF_UP);
        BigDecimal fee = row.getFlatFee().add(variable);
        fee = fee.max(row.getMinFee()).min(row.getMaxFee());
        return new FeeBreakdown(MoneyUtil.round(fee, currency), rail, row.getFlatFee(), row.getPctFee(),
                row.getMinFee(), row.getMaxFee());
    }

    /**
     * Evaluates the desk's surcharge rule against a quote context, so pricing can be adjusted per
     * counterparty without a release of this service.
     */
    public BigDecimal computeSurcharge(String rule, BigDecimal amount, String currency) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("amount", amount);
            context.put("currency", currency);
            Object result = Ognl.getValue(rule, context, context);
            return new BigDecimal(String.valueOf(result));
        } catch (OgnlException | NumberFormatException e) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "The surcharge rule could not be evaluated");
        }
    }
}
