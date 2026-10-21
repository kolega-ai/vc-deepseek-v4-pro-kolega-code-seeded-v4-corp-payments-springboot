package com.northbridge.paymentapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Fixed-precision money helpers. All monetary values in this application are BigDecimal with a
 * scale bound by the currency's minor unit; floating point is never used for money.
 */
public final class MoneyUtil {

    public static final Map<String, Integer> MINOR_UNITS = Map.of(
            "GBP", 2, "EUR", 2, "USD", 2);

    private MoneyUtil() {
    }

    public static int scale(String currency) {
        return MINOR_UNITS.getOrDefault(currency == null ? "" : currency.toUpperCase(Locale.ROOT), 2);
    }

    /** Rounds to the currency minor unit using HALF_UP (banker display convention). */
    public static BigDecimal round(BigDecimal value, String currency) {
        return value.setScale(scale(currency), RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** amount / 2 rounded up to the currency minor unit (dual-approval "half"). */
    public static BigDecimal halfUp(BigDecimal amount, String currency) {
        return amount.divide(BigDecimal.valueOf(2), scale(currency), RoundingMode.HALF_UP);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.min(b);
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.max(b);
    }
}
