package com.northbridge.paymentapi;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Test-data builder: seeds two companies, users, accounts, beneficiaries and reference tables. */
@Component
public class TestDataBuilder {

    public static final UUID COMPANY_A = UUID.fromString("10000000-0000-4000-8000-0000000000a1");
    public static final UUID COMPANY_B = UUID.fromString("10000000-0000-4000-8000-0000000000a2");

    public static final UUID MAKER_A = UUID.fromString("10000000-0000-4000-8000-000000000b01");
    public static final UUID MAKER_A_RESTRICTED = UUID.fromString("10000000-0000-4000-8000-000000000b02");
    public static final UUID CHECKER_A_BIG = UUID.fromString("10000000-0000-4000-8000-000000000b03");
    public static final UUID CHECKER_A_SMALL = UUID.fromString("10000000-0000-4000-8000-000000000b04");
    public static final UUID ADMIN_A = UUID.fromString("10000000-0000-4000-8000-000000000b05");
    public static final UUID MAKER_B = UUID.fromString("10000000-0000-4000-8000-000000000b06");
    public static final UUID CHECKER_B = UUID.fromString("10000000-0000-4000-8000-000000000b07");
    public static final UUID OPS = UUID.fromString("10000000-0000-4000-8000-000000000b08");
    public static final UUID BANK_ADMIN = UUID.fromString("10000000-0000-4000-8000-000000000b09");

    public static final UUID ACCOUNT_A1 = UUID.fromString("10000000-0000-4000-8000-000000000c01");
    public static final UUID ACCOUNT_A2 = UUID.fromString("10000000-0000-4000-8000-000000000c02");
    public static final UUID ACCOUNT_B1 = UUID.fromString("10000000-0000-4000-8000-000000000c03");

    private final CompanyRepository companies;
    private final CompanyUserRepository users;
    private final AccountRepository accounts;
    private final BeneficiaryRepository beneficiaries;
    private final FeeScheduleRepository fees;
    private final FxRateRepository fx;

    public TestDataBuilder(CompanyRepository companies, CompanyUserRepository users,
                           AccountRepository accounts, BeneficiaryRepository beneficiaries,
                           FeeScheduleRepository fees, FxRateRepository fx) {
        this.companies = companies;
        this.users = users;
        this.accounts = accounts;
        this.beneficiaries = beneficiaries;
        this.fees = fees;
        this.fx = fx;
    }

    @Transactional
    public void seed() {
        Company a = new Company(COMPANY_A, "Alpha Test Co", "AA-000001", "GBP", new BigDecimal("50000.00"));
        Company b = new Company(COMPANY_B, "Beta Test Co", "BB-000002", "GBP", new BigDecimal("100000.00"));
        companies.save(a);
        companies.save(b);

        accounts.save(new Account(ACCOUNT_A1, "10000001", a, "GBP", "BUSINESS_CURRENT", "Main"));
        accounts.save(new Account(ACCOUNT_A2, "10000002", a, "GBP", "BUSINESS_CURRENT", "Payroll"));
        accounts.save(new Account(ACCOUNT_B1, "20000001", b, "GBP", "BUSINESS_CURRENT", "Main"));

        user(MAKER_A, "maker@alpha.test", "Alpha Maker", Role.COMPANY_MAKER, "0", a, Set.of());
        user(MAKER_A_RESTRICTED, "maker2@alpha.test", "Alpha Maker 2", Role.COMPANY_MAKER, "0", a,
                Set.of(ACCOUNT_A2));
        user(CHECKER_A_BIG, "checker@alpha.test", "Alpha Checker", Role.COMPANY_CHECKER, "25000.00", a, Set.of());
        user(CHECKER_A_SMALL, "checker2@alpha.test", "Alpha Checker 2", Role.COMPANY_CHECKER, "10000.00", a, Set.of());
        user(ADMIN_A, "admin@alpha.test", "Alpha Admin", Role.COMPANY_ADMIN, "0", a, Set.of());
        user(MAKER_B, "maker@beta.test", "Beta Maker", Role.COMPANY_MAKER, "0", b, Set.of());
        user(CHECKER_B, "checker@beta.test", "Beta Checker", Role.COMPANY_CHECKER, "5000.00", b, Set.of());
        user(OPS, "ops@bank.test", "Bank Ops", Role.BANK_OPS, "0", null, Set.of());
        user(BANK_ADMIN, "bankadmin@bank.test", "Bank Admin", Role.BANK_ADMIN, "0", null, Set.of());

        beneficiary("90000001", a, "Alpha Beneficiary", "GBP", BeneficiaryStatus.ACTIVE, null);
        beneficiary("90000002", a, "Cooling Beneficiary", "GBP", BeneficiaryStatus.PENDING_COOLING,
                Instant.now().plusSeconds(3600));
        beneficiary("90000003", b, "Beta Beneficiary", "GBP", BeneficiaryStatus.ACTIVE, null);

        fees.save(new FeeScheduleRow(UUID.randomUUID(), PaymentRail.FASTER_PAYMENTS, "GBP",
                new BigDecimal("0.50"), new BigDecimal("0.10"), new BigDecimal("0.50"),
                new BigDecimal("25.00"), Instant.parse("2026-01-01T00:00:00Z")));
        fees.save(new FeeScheduleRow(UUID.randomUUID(), PaymentRail.SEPA, "GBP",
                new BigDecimal("1.50"), new BigDecimal("0.25"), new BigDecimal("1.50"),
                new BigDecimal("50.00"), Instant.parse("2026-01-01T00:00:00Z")));
        fx.save(new FxRateRow(UUID.randomUUID(), "GBP/EUR", new BigDecimal("1.1650"),
                Instant.parse("2026-08-01T00:00:00Z")));
    }

    private void user(UUID id, String email, String name, Role role, String limit, Company company,
                      Set<UUID> accountIds) {
        CompanyUser user = new CompanyUser(id, email, name, role, UserStatus.ACTIVE, new BigDecimal(limit), company);
        users.save(user);
        if (!accountIds.isEmpty()) {
            user.setAssignedAccounts(accountIds.stream().map(a -> accounts.findById(a).orElseThrow())
                    .collect(Collectors.toSet()));
            users.save(user);
        }
    }

    private void beneficiary(String accountNumber, Company company, String name, String currency,
                             BeneficiaryStatus status, Instant coolingUntil) {
        beneficiaries.save(new Beneficiary(UUID.randomUUID(), company, name, accountNumber,
                "20-00-00", currency, status, coolingUntil, null));
    }

    @Transactional(readOnly = true)
    public AuthPrincipal principal(UUID userId) {
        CompanyUser user = users.findById(userId).orElseThrow();
        Set<UUID> accounts = user.getAssignedAccounts().stream()
                .map(BaseEntity::getId).collect(Collectors.toSet());
        return new AuthPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCompany() == null ? null : user.getCompany().getId(),
                user.getApprovalLimit(), accounts, false);
    }
}
