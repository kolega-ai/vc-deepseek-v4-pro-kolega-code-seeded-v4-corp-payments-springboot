package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.Company;
import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.PaymentStatus;
import com.northbridge.paymentapi.repo.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Company daily limit / headroom (precise):
 *
 * <p>utilisation = sum of settlementAmount + fee, converted to the company base currency at the
 * payment's stored fxRate, over all payments in status APPROVED, QUEUED_FOR_RELEASE or RELEASED
 * whose value date falls in the current UTC calendar day. A reversal reaching POSTED moves the
 * payment to REVERSED, which restores its contribution. REJECTED and RETURNED payments consume
 * nothing.</p>
 */
@Service
public class LimitService {

    private final PaymentRepository paymentRepository;
    private final FxService fxService;

    public LimitService(PaymentRepository paymentRepository, FxService fxService) {
        this.paymentRepository = paymentRepository;
        this.fxService = fxService;
    }

    public record Utilisation(BigDecimal used, BigDecimal limit, BigDecimal headroom, String currency) {
        public int percent() {
            if (limit == null || limit.signum() == 0) {
                return 0;
            }
            return used.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).intValue();
        }
    }

    private static final List<PaymentStatus> COUNTED = List.of(
            PaymentStatus.APPROVED, PaymentStatus.QUEUED_FOR_RELEASE, PaymentStatus.RELEASED);

    public Utilisation utilisation(UUID companyId, Company company, LocalDate valueDate) {
        List<Payment> counted = paymentRepository.findForUtilisation(companyId, COUNTED, valueDate);
        BigDecimal used = BigDecimal.ZERO;
        for (Payment payment : counted) {
            BigDecimal converted = fxService.toBaseCurrency(
                    payment.getSettlementAmount(), payment.getFxRate(), company.getBaseCurrency());
            used = used.add(converted).add(payment.getFee());
        }
        used = MoneyUtil.round(used, company.getBaseCurrency());
        BigDecimal headroom = company.getDailyLimit().subtract(used);
        return new Utilisation(used, company.getDailyLimit(), headroom, company.getBaseCurrency());
    }

    public Utilisation utilisationToday(UUID companyId, Company company) {
        return utilisation(companyId, company, LocalDate.now(ZoneOffset.UTC));
    }

    /** The base-currency amount a single payment contributes to utilisation (settlement + fee). */
    public BigDecimal contribution(Payment payment) {
        BigDecimal converted = fxService.toBaseCurrency(
                payment.getSettlementAmount(), payment.getFxRate(), payment.getCompany().getBaseCurrency());
        return MoneyUtil.round(converted.add(payment.getFee()), payment.getCompany().getBaseCurrency());
    }
}
