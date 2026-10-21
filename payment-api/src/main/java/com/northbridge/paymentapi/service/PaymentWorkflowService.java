package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.BeneficiaryRepository;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.repo.PaymentRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.state.PaymentStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Payment workflow: create, fee/FX preview, submit, amend and the single audited transition
 * helper every state change goes through. Money fields (fee, fxRate, settlementAmount) are
 * computed server-side from seeded reference tables and are NEVER accepted from a client.
 */
@Service
public class PaymentWorkflowService {

    private final PaymentRepository paymentRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final CompanyUserRepository userRepository;
    private final AccessControlService access;
    private final FeeService feeService;
    private final FxService fxService;
    private final LimitService limitService;
    private final AuditService auditService;
    private final NotificationService notifications;

    public PaymentWorkflowService(PaymentRepository paymentRepository,
                                  BeneficiaryRepository beneficiaryRepository,
                                  CompanyUserRepository userRepository,
                                  AccessControlService access,
                                  FeeService feeService,
                                  FxService fxService,
                                  LimitService limitService,
                                  AuditService auditService,
                                  NotificationService notifications) {
        this.paymentRepository = paymentRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.feeService = feeService;
        this.fxService = fxService;
        this.limitService = limitService;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    public record PaymentDraft(BigDecimal amount, String currency, Account debitAccount,
                               Beneficiary beneficiary, LocalDate valueDate, String reference) {
    }

    public record FeePreview(BigDecimal amount, String currency, BigDecimal fee, PaymentRail rail,
                             BigDecimal fxRate, BigDecimal settlementAmount, String settlementCurrency,
                             FeeService.FeeBreakdown breakdown) {
    }

    // ------------------------------------------------------------------ preview

    /** Server-computed fee and FX preview; nothing here is taken from the request. */
    @Transactional(readOnly = true)
    public FeePreview preview(AuthPrincipal actor, UUID debitAccountId, UUID beneficiaryId, BigDecimal amount) {
        Account account = access.requireUsableAccount(actor, debitAccountId);
        Beneficiary beneficiary = access.requireBeneficiaryAccess(actor, beneficiaryId);
        access.requireActiveCompany(account.getCompany());
        validateAmount(amount, account.getCurrency());
        PaymentRail rail = FeeService.railFor(account.getCurrency(), beneficiary.getCurrency());
        FeeService.FeeBreakdown breakdown = feeService.compute(rail, account.getCurrency(), amount, Instant.now());
        FxService.Conversion conversion = fxService.convert(account.getCurrency(), beneficiary.getCurrency(), amount, Instant.now());
        return new FeePreview(amount, account.getCurrency(), breakdown.fee(), rail,
                conversion.fxRate(), conversion.settlementAmount(), conversion.settlementCurrency(), breakdown);
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public UUID createDraft(AuthPrincipal actor, UUID debitAccountId, UUID beneficiaryId,
                            BigDecimal amount, LocalDate valueDate, String reference) {
        if (actor.role() != Role.COMPANY_MAKER) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_maker can create payments");
        }
        Account account = access.requireUsableAccount(actor, debitAccountId);
        Beneficiary beneficiary = access.requireBeneficiaryAccess(actor, beneficiaryId);
        access.requireActiveCompany(account.getCompany());
        if (!beneficiary.getCompany().getId().equals(account.getCompany().getId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found");
        }
        validateAmount(amount, account.getCurrency());
        requirePayable(beneficiary, Instant.now());
        validateValueDate(valueDate);
        String cleanReference = cleanReference(reference);

        Payment payment = new Payment(
                UUID.randomUUID(), nextRef(account.getCompany()), account.getCompany(), account,
                beneficiary, MoneyUtil.round(amount, account.getCurrency()), account.getCurrency(),
                valueDate, cleanReference, PaymentStatus.DRAFT,
                actor.companyId() == null ? null : userRef(actor));
        applyPricing(payment, Instant.now());
        paymentRepository.save(payment);

        auditService.audit(actor, "PAYMENT_CREATED", "payment", payment.getId(), payment.getRef(),
                "Payment draft created for " + amount + " " + payment.getCurrency(),
                null, Map.of("status", "draft", "amount", amount, "ref", payment.getRef()));
        return payment.getId();
    }

    // ------------------------------------------------------------------ submit

    @Transactional
    public void submit(AuthPrincipal actor, UUID paymentId) {
        if (actor.role() != Role.COMPANY_MAKER) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_maker can submit payments");
        }
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        access.requireActiveCompany(payment.getCompany());
        // A beneficiary in its cooling period cannot receive a payment — checked before any
        // state change so a failed submit leaves the payment untouched.
        if (!isPayable(payment.getBeneficiary(), Instant.now())) {
            throw new ApiException(ApiException.Code.BENEFICIARY_NOT_PAYABLE,
                    "Beneficiary " + payment.getBeneficiary().getName() + " is not payable yet");
        }
        transition(payment, PaymentStatus.PENDING_APPROVAL, actor,
                "PAYMENT_SUBMITTED", "Payment submitted for approval", "maker_submit");
        notifications.notifyCompanyRole(payment.getCompany().getId(), Role.COMPANY_CHECKER,
                NotificationType.APPROVAL_PENDING,
                "Payment " + payment.getRef() + " (" + payment.getAmount() + " " + payment.getCurrency()
                        + ") is awaiting approval",
                "/payments/" + payment.getId());
    }

    // ------------------------------------------------------------------ amend

    /** Maker amends a returned payment: returned→draft with a recorded amendment history. */
    @Transactional
    public void amend(AuthPrincipal actor, UUID paymentId, BigDecimal newAmount, LocalDate newValueDate,
                      String newReference, UUID newBeneficiaryId) {
        if (actor.role() != Role.COMPANY_MAKER) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_maker can amend payments");
        }
        Payment payment = access.requirePaymentAccess(actor, paymentId);
        if (payment.getStatus() != PaymentStatus.RETURNED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Only a returned payment can be amended");
        }
        List<PaymentAmendment.ChangedField> changed = new ArrayList<>();
        if (newAmount != null && newAmount.compareTo(payment.getAmount()) != 0) {
            validateAmount(newAmount, payment.getCurrency());
            changed.add(new PaymentAmendment.ChangedField("amount",
                    payment.getAmount().toPlainString(), newAmount.toPlainString()));
            payment.setAmount(MoneyUtil.round(newAmount, payment.getCurrency()));
        }
        if (newValueDate != null && !newValueDate.equals(payment.getValueDate())) {
            validateValueDate(newValueDate);
            changed.add(new PaymentAmendment.ChangedField("valueDate",
                    payment.getValueDate().toString(), newValueDate.toString()));
            payment.setValueDate(newValueDate);
        }
        if (newReference != null) {
            String cleaned = cleanReference(newReference);
            if (!cleaned.equals(payment.getReference())) {
                changed.add(new PaymentAmendment.ChangedField("reference",
                        payment.getReference(), cleaned));
                payment.setReference(cleaned);
            }
        }
        if (newBeneficiaryId != null && !newBeneficiaryId.equals(payment.getBeneficiary().getId())) {
            Beneficiary beneficiary = access.requireBeneficiaryAccess(actor, newBeneficiaryId);
            requirePayable(beneficiary, Instant.now());
            changed.add(new PaymentAmendment.ChangedField("beneficiary",
                    beneficiaryLabel(payment.getBeneficiary()), beneficiaryLabel(beneficiary)));
            payment.setBeneficiary(beneficiary);
        }
        if (changed.isEmpty()) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "No changes supplied");
        }
        PaymentAmendment amendment = new PaymentAmendment(UUID.randomUUID(), payment,
                userRef(actor), changed);
        payment.getAmendments().add(amendment);
        payment.setAmendedBy(userRef(actor));
        applyPricing(payment, Instant.now());
        transition(payment, PaymentStatus.DRAFT, actor, "PAYMENT_AMENDED",
                "Payment returned to draft by maker amendment", "maker_amend");
    }

    // ------------------------------------------------------------------ shared

    /**
     * The single state-transition gate. Every payment status change (runtime and seeded) passes
     * through here; the allowed-edge list is enforced and an audit event is written.
     */
    @Transactional
    public void transition(Payment payment, PaymentStatus to, AuthPrincipal actor, String action,
                           String summary, String basisSuffix) {
        PaymentStatus from = payment.getStatus();
        PaymentStateMachine.require(from, to);
        payment.setStatus(to);
        payment.setUpdatedAt(Instant.now());
        Map<String, Object> before = Map.of("status", from.name());
        Map<String, Object> after = Map.of("status", to.name());
        auditService.audit(actor, action, "payment", payment.getId(), payment.getRef(),
                summary, before, after, actor.authBasis() + " basis=" + basisSuffix);
        paymentRepository.saveAndFlush(payment);
    }

    /** System-actor transition (batch release, cooling elapse, seeding). */
    @Transactional
    public void transitionSystem(Payment payment, PaymentStatus to, String systemName, String action,
                                 String summary, String basisSuffix) {
        PaymentStatus from = payment.getStatus();
        PaymentStateMachine.require(from, to);
        payment.setStatus(to);
        payment.setUpdatedAt(Instant.now());
        auditService.auditSystem(systemName, action, "payment", payment.getId(), payment.getRef(),
                summary, Map.of("status", from.name()), Map.of("status", to.name()),
                "system=" + systemName + " basis=" + basisSuffix);
    }

    /** Recomputes fee/fx/settlement server-side and stores them on the payment. */
    public void applyPricing(Payment payment, Instant at) {
        PaymentRail rail = FeeService.railFor(payment.getCurrency(), payment.getBeneficiary().getCurrency());
        FeeService.FeeBreakdown breakdown = feeService.compute(rail, payment.getCurrency(), payment.getAmount(), at);
        FxService.Conversion conversion = fxService.convert(payment.getCurrency(),
                payment.getBeneficiary().getCurrency(), payment.getAmount(), at);
        payment.setFee(breakdown.fee());
        payment.setFxRate(conversion.fxRate());
        payment.setSettlementAmount(conversion.settlementAmount());
        payment.setProductType(rail);
    }

    public boolean isPayable(Beneficiary beneficiary, Instant at) {
        return beneficiary.getStatus() == BeneficiaryStatus.ACTIVE
                && (beneficiary.getCoolingUntil() == null || !beneficiary.getCoolingUntil().isAfter(at));
    }

    public void requirePayable(Beneficiary beneficiary, Instant at) {
        if (!isPayable(beneficiary, at)) {
            throw new ApiException(ApiException.Code.BENEFICIARY_NOT_PAYABLE,
                    "Beneficiary " + beneficiary.getName() + " is in its cooling period or suspended "
                            + "(cooling ends " + beneficiary.getCoolingUntil() + ")");
        }
    }

    public void validateAmount(BigDecimal amount, String currency) {
        if (!MoneyUtil.isPositive(amount)) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Amount must be positive");
        }
        if (amount.scale() > MoneyUtil.scale(currency)) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Amount exceeds the currency's minor-unit precision");
        }
    }

    public void validateValueDate(LocalDate valueDate) {
        if (valueDate == null) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Value date is required");
        }
        if (valueDate.isBefore(LocalDate.now())) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Value date cannot be in the past");
        }
    }

    public String cleanReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Reference is required");
        }
        String cleaned = reference.strip();
        if (cleaned.length() > 140) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Reference is too long (max 140)");
        }
        return cleaned;
    }

    private CompanyUser userRef(AuthPrincipal actor) {
        if (actor == null || actor.userId() == null) {
            return null;
        }
        return userRepository.findById(actor.userId()).orElse(null);
    }

    private String nextRef(Company company) {
        // Human-readable reference: PY-<company code>-<sequence>. Company code is derived from the
        // legal name in the seeder; runtime refs use a random 4-digit suffix with collision retry.
        String prefix = "PY-" + companyCode(company);
        for (int i = 0; i < 5; i++) {
            String candidate = prefix + "-" + String.format("%04d", new java.security.SecureRandom().nextInt(10000));
            if (paymentRepository.findByRef(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ApiException(ApiException.Code.INTERNAL_ERROR, "Could not allocate payment reference");
    }

    private static String beneficiaryLabel(Beneficiary beneficiary) {
        return beneficiary.getName() + " (" + beneficiary.getAccountNumber() + ")";
    }

    private String companyCode(Company company) {
        return company.getLegalName().replaceAll("[^A-Za-z]", "").toUpperCase();
    }
}
