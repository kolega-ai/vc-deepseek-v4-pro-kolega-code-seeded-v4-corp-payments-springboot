package com.northbridge.paymentapi.seed;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.service.*;
import com.northbridge.paymentapi.state.PaymentStateMachine;
import com.northbridge.paymentapi.state.ReversalStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo data for the Corporate Payments Portal.
 *
 * <p>Every seeded status is PRODUCED BY A STATED ACTION: payments are created as drafts, submitted
 * through the workflow gate, approved/rejected/returned through {@link ApprovalService}, queued and
 * released through the same transition gates used at runtime (with the core posting outcome
 * simulated — see {@link #simulateRelease}), and bulk files are processed by
 * {@link BulkFileService#process}. No status is seeded directly.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CompanyRepository companyRepository;
    private final CompanyUserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final FeeScheduleRepository feeScheduleRepository;
    private final FxRateRepository fxRateRepository;
    private final PaymentRepository paymentRepository;
    private final BulkFileRepository bulkFileRepository;
    private final BulkFileLineRepository lineRepository;
    private final ReleaseRepository releaseRepository;
    private final ReversalRepository reversalRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final PaymentWorkflowService workflow;
    private final ApprovalService approvalService;
    private final BulkFileService bulkFileService;
    private final ReversalService reversalService;
    private final AuditService auditService;
    private final NotificationService notifications;

    public DemoDataSeeder(CompanyRepository companyRepository,
                          CompanyUserRepository userRepository,
                          AccountRepository accountRepository,
                          BeneficiaryRepository beneficiaryRepository,
                          FeeScheduleRepository feeScheduleRepository,
                          FxRateRepository fxRateRepository,
                          PaymentRepository paymentRepository,
                          BulkFileRepository bulkFileRepository,
                          BulkFileLineRepository lineRepository,
                          ReleaseRepository releaseRepository,
                          ReversalRepository reversalRepository,
                          WebhookConfigRepository webhookConfigRepository,
                          WebhookDeliveryRepository webhookDeliveryRepository,
                          PaymentWorkflowService workflow,
                          ApprovalService approvalService,
                          BulkFileService bulkFileService,
                          ReversalService reversalService,
                          AuditService auditService,
                          NotificationService notifications) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.feeScheduleRepository = feeScheduleRepository;
        this.fxRateRepository = fxRateRepository;
        this.paymentRepository = paymentRepository;
        this.bulkFileRepository = bulkFileRepository;
        this.lineRepository = lineRepository;
        this.releaseRepository = releaseRepository;
        this.reversalRepository = reversalRepository;
        this.webhookConfigRepository = webhookConfigRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.workflow = workflow;
        this.approvalService = approvalService;
        this.bulkFileService = bulkFileService;
        this.reversalService = reversalService;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ entry

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.count() > 0) {
            log.info("payment-api seed skipped: data already present");
            return;
        }
        log.info("Seeding payment-api demo data...");
        seedCompanies();
        seedAccounts();
        seedUsers();
        seedBeneficiaries();
        seedReferenceTables();
        seedBulkFiles();
        seedPayments();
        seedReleasesAndReversals();
        seedNotificationsAndWebhooks();
        log.info("payment-api seed complete");
    }

    // ------------------------------------------------------------------ companies & users

    private void seedCompanies() {
        company(SeedRefs.COMPANY_ACME, "Acme Industrials Ltd", "AC-0142337", "50000.00");
        company(SeedRefs.COMPANY_BRIGHTON, "Brighton Retail Group Plc", "BR-9023148", "250000.00");
        company(SeedRefs.COMPANY_CRANFIELD, "Cranfield Logistics Ltd", "CR-5510290", "1000000.00");
        company(SeedRefs.COMPANY_DELOS, "Delos Energy Holdings Ltd", "DE-7704615", "5000000.00");
    }

    private void company(UUID id, String name, String reg, String limit) {
        Company company = new Company(id, name, reg, "GBP", new BigDecimal(limit));
        companyRepository.save(company);
        auditService.auditSystemForCompany("seed-data", id, "COMPANY_ONBOARDED", "company", id, null,
                "Demo company onboarded", null, Map.of("legalName", name), "system=seed-data basis=seeding");
    }

    private void seedUsers() {
        user(SeedRefs.USER_ACME_MAKER1, "maker1@acme.example", "Alice Foster", Role.COMPANY_MAKER, "0",
                SeedRefs.COMPANY_ACME, Set.of());
        user(SeedRefs.USER_ACME_MAKER2, "maker2@acme.example", "Ben Marsh", Role.COMPANY_MAKER, "0",
                SeedRefs.COMPANY_ACME, Set.of(SeedRefs.ACCOUNT_ACME_PAYROLL));
        user(SeedRefs.USER_ACME_CHECKER1, "checker1@acme.example", "Claire Dent", Role.COMPANY_CHECKER, "25000.00",
                SeedRefs.COMPANY_ACME, Set.of());
        user(SeedRefs.USER_ACME_CHECKER2, "checker2@acme.example", "Daniel Osei", Role.COMPANY_CHECKER, "20000.00",
                SeedRefs.COMPANY_ACME, Set.of());
        user(SeedRefs.USER_ACME_ADMIN, "admin1@acme.example", "Emma Lin", Role.COMPANY_ADMIN, "0",
                SeedRefs.COMPANY_ACME, Set.of());
        user(SeedRefs.USER_BRIGHTON_MAKER, "maker1@brighton.example", "Fiona Grant", Role.COMPANY_MAKER, "0",
                SeedRefs.COMPANY_BRIGHTON, Set.of());
        user(SeedRefs.USER_BRIGHTON_CHECKER, "checker1@brighton.example", "George Patel", Role.COMPANY_CHECKER,
                "150000.00", SeedRefs.COMPANY_BRIGHTON, Set.of());
        user(SeedRefs.USER_BRIGHTON_ADMIN, "admin1@brighton.example", "Hannah Reed", Role.COMPANY_ADMIN, "0",
                SeedRefs.COMPANY_BRIGHTON, Set.of());
        user(SeedRefs.USER_BRIGHTON_INVITED, "invited.user@brighton.example", "Iris Wong", Role.COMPANY_CHECKER,
                "50000.00", SeedRefs.COMPANY_BRIGHTON, Set.of(SeedRefs.ACCOUNT_BRIGHTON_TRADING), UserStatus.INVITED);
        user(SeedRefs.USER_CRANFIELD_MAKER, "maker1@cranfield.example", "Ian Shaw", Role.COMPANY_MAKER, "0",
                SeedRefs.COMPANY_CRANFIELD, Set.of());
        user(SeedRefs.USER_CRANFIELD_CHECKER, "checker1@cranfield.example", "Julia Kim", Role.COMPANY_CHECKER,
                "500000.00", SeedRefs.COMPANY_CRANFIELD, Set.of());
        user(SeedRefs.USER_CRANFIELD_ADMIN, "admin1@cranfield.example", "Karl Weber", Role.COMPANY_ADMIN, "0",
                SeedRefs.COMPANY_CRANFIELD, Set.of());
        user(SeedRefs.USER_DELOS_MAKER, "maker1@delos.example", "Laura Nunes", Role.COMPANY_MAKER, "0",
                SeedRefs.COMPANY_DELOS, Set.of());
        user(SeedRefs.USER_DELOS_CHECKER, "checker1@delos.example", "Miguel Santos", Role.COMPANY_CHECKER,
                "2000000.00", SeedRefs.COMPANY_DELOS, Set.of());
        user(SeedRefs.USER_DELOS_ADMIN, "admin1@delos.example", "Nadia Hasan", Role.COMPANY_ADMIN, "0",
                SeedRefs.COMPANY_DELOS, Set.of());
        // Bank-wide roles (no company).
        user(SeedRefs.USER_OPS1, "ops1@bank.example", "Omar Yusuf", Role.BANK_OPS, "0", null, Set.of());
        user(SeedRefs.USER_OPS2, "ops2@bank.example", "Priya Nair", Role.BANK_OPS, "0", null, Set.of());
        user(SeedRefs.USER_BANK_ADMIN, "bankadmin1@bank.example", "Quentin Bell", Role.BANK_ADMIN, "0", null, Set.of());
    }

    private void user(UUID id, String email, String name, Role role, String limit, UUID companyId,
                      Set<UUID> accountIds) {
        user(id, email, name, role, limit, companyId, accountIds, UserStatus.ACTIVE);
    }

    private void user(UUID id, String email, String name, Role role, String limit, UUID companyId,
                      Set<UUID> accountIds, UserStatus status) {
        Company company = companyId == null ? null : companyRepository.findById(companyId).orElseThrow();
        CompanyUser user = new CompanyUser(id, email, name, role, status, new BigDecimal(limit), company);
        userRepository.save(user);
        if (!accountIds.isEmpty()) {
            user.setAssignedAccounts(accountIds.stream()
                    .map(accountRepository::findById)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet()));
            userRepository.save(user);
        }
    }

    private void seedAccounts() {
        account(SeedRefs.ACCOUNT_ACME_OPS, "20301001", SeedRefs.COMPANY_ACME, "GBP", "Operations");
        account(SeedRefs.ACCOUNT_ACME_PAYROLL, "20301002", SeedRefs.COMPANY_ACME, "GBP", "Payroll");
        account(SeedRefs.ACCOUNT_ACME_LOWBAL, "20301003", SeedRefs.COMPANY_ACME, "GBP", "Low Balance Test");
        account(SeedRefs.ACCOUNT_BRIGHTON_TRADING, "20442011", SeedRefs.COMPANY_BRIGHTON, "GBP", "Trading");
        account(SeedRefs.ACCOUNT_BRIGHTON_FLOAT, "20442012", SeedRefs.COMPANY_BRIGHTON, "GBP", "Float");
        account(SeedRefs.ACCOUNT_BRIGHTON_EUR, "20442013", SeedRefs.COMPANY_BRIGHTON, "EUR", "Euro Working");
        account(SeedRefs.ACCOUNT_CRANFIELD_MAIN, "20881021", SeedRefs.COMPANY_CRANFIELD, "GBP", "Main");
        account(SeedRefs.ACCOUNT_CRANFIELD_COLLECTIONS, "20881022", SeedRefs.COMPANY_CRANFIELD, "GBP", "Collections");
        account(SeedRefs.ACCOUNT_CRANFIELD_TREASURY, "20881023", SeedRefs.COMPANY_CRANFIELD, "GBP", "Treasury");
        account(SeedRefs.ACCOUNT_DELOS_OPERATING, "21552031", SeedRefs.COMPANY_DELOS, "GBP", "Operating");
        account(SeedRefs.ACCOUNT_DELOS_CAPITAL, "21552032", SeedRefs.COMPANY_DELOS, "GBP", "Capital");
        account(SeedRefs.ACCOUNT_DELOS_USD, "21552033", SeedRefs.COMPANY_DELOS, "USD", "US Operations");
    }

    private void account(UUID id, String number, UUID companyId, String currency, String name) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        accountRepository.save(new Account(id, number, company, currency, "BUSINESS_CURRENT", name));
    }

    private void seedBeneficiaries() {
        // Acme
        beneficiary("b01", SeedRefs.COMPANY_ACME, "Harrow Steel Supplies Ltd", "30992210", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_ACME_ADMIN);
        beneficiary("b02", SeedRefs.COMPANY_ACME, "Vertex Office Interiors", "30992211", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_ACME_ADMIN);
        beneficiary("b03", SeedRefs.COMPANY_ACME, "Meridian Utilities", "30992212", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_ACME_ADMIN);
        // Cooling ends 5 minutes after seeding — the "pay after cooling" review journey.
        beneficiary("b04", SeedRefs.COMPANY_ACME, "Swift Couriers GB", "30992213", "20-00-00", "GBP",
                BeneficiaryStatus.PENDING_COOLING, Instant.now().plus(5, ChronoUnit.MINUTES),
                SeedRefs.USER_ACME_ADMIN);
        beneficiary("b05", SeedRefs.COMPANY_ACME, "Nordic Trade AB", "51230456", "SEB-500", "EUR",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_ACME_ADMIN);
        beneficiary("b16", SeedRefs.COMPANY_ACME, "Legacy Frozen Supplies", "30992214", "20-00-00", "GBP",
                BeneficiaryStatus.SUSPENDED, null, SeedRefs.USER_ACME_ADMIN);
        // Brighton
        beneficiary("b06", SeedRefs.COMPANY_BRIGHTON, "Coastal Wholesale Ltd", "40123876", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_BRIGHTON_ADMIN);
        beneficiary("b07", SeedRefs.COMPANY_BRIGHTON, "Pulse Marketing Ltd", "40123877", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_BRIGHTON_ADMIN);
        beneficiary("b08", SeedRefs.COMPANY_BRIGHTON, "Northwind Distribution", "40123878", "20-00-00", "GBP",
                BeneficiaryStatus.SUSPENDED, null, SeedRefs.USER_BRIGHTON_ADMIN);
        // Cranfield
        beneficiary("b09", SeedRefs.COMPANY_CRANFIELD, "Euro Logistics GmbH", "74322011", "DEUTDEFF", "EUR",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_CRANFIELD_ADMIN);
        beneficiary("b10", SeedRefs.COMPANY_CRANFIELD, "Atlas Freight Ltd", "41099877", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_CRANFIELD_ADMIN);
        beneficiary("b11", SeedRefs.COMPANY_CRANFIELD, "Harbour Marine Services", "41099878", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_CRANFIELD_ADMIN);
        beneficiary("b12", SeedRefs.COMPANY_CRANFIELD, "TempLine Staffing", "41099879", "20-00-00", "GBP",
                BeneficiaryStatus.PENDING_COOLING, Instant.now().plus(2, ChronoUnit.HOURS),
                SeedRefs.USER_CRANFIELD_ADMIN);
        // Delos
        beneficiary("b13", SeedRefs.COMPANY_DELOS, "IonGrid Power Systems", "52210034", "20-00-00", "GBP",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_DELOS_ADMIN);
        beneficiary("b14", SeedRefs.COMPANY_DELOS, "Stratos Wind GmbH", "90211045", "DEUTDEFF", "EUR",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_DELOS_ADMIN);
        beneficiary("b15", SeedRefs.COMPANY_DELOS, "Meridian Exchange Corp", "065544210", "CHASUS33", "USD",
                BeneficiaryStatus.ACTIVE, null, SeedRefs.USER_DELOS_ADMIN);
    }

    private void beneficiary(String idSuffix, UUID companyId, String name, String accountNumber,
                             String bankCode, String currency, BeneficiaryStatus status,
                             Instant coolingUntil, UUID createdBy) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        CompanyUser creator = userRepository.findById(createdBy).orElse(null);
        Beneficiary b = new Beneficiary(UUID.randomUUID(), company, name, accountNumber, bankCode, currency,
                status, coolingUntil, creator);
        if (status == BeneficiaryStatus.ACTIVE) {
            b.setCreatedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        }
        beneficiaryRepository.save(b);
    }

    private void seedReferenceTables() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        feeScheduleRepository.save(new FeeScheduleRow(UUID.randomUUID(), PaymentRail.FASTER_PAYMENTS, "GBP",
                new BigDecimal("0.50"), new BigDecimal("0.10"), new BigDecimal("0.50"),
                new BigDecimal("25.00"), from));
        feeScheduleRepository.save(new FeeScheduleRow(UUID.randomUUID(), PaymentRail.SEPA, "GBP",
                new BigDecimal("1.50"), new BigDecimal("0.25"), new BigDecimal("1.50"),
                new BigDecimal("50.00"), from));
        feeScheduleRepository.save(new FeeScheduleRow(UUID.randomUUID(), PaymentRail.SWIFT, "GBP",
                new BigDecimal("3.00"), new BigDecimal("0.40"), new BigDecimal("3.00"),
                new BigDecimal("100.00"), from));

        Instant aug = Instant.parse("2026-08-01T00:00:00Z");
        fxRateRepository.save(new FxRateRow(UUID.randomUUID(), "GBP/EUR", new BigDecimal("1.1580"), from));
        fxRateRepository.save(new FxRateRow(UUID.randomUUID(), "GBP/USD", new BigDecimal("1.2640"), from));
        fxRateRepository.save(new FxRateRow(UUID.randomUUID(), "GBP/EUR", new BigDecimal("1.1650"), aug));
        fxRateRepository.save(new FxRateRow(UUID.randomUUID(), "GBP/USD", new BigDecimal("1.2720"), aug));
    }

    // ------------------------------------------------------------------ bulk files

    private void seedBulkFiles() {
        AuthPrincipal maker = principal(user(SeedRefs.USER_ACME_MAKER1));

        // f1: clean file — all three lines valid, approved and released.
        BulkFile f1 = bulkFileService.upload(maker, "supplier-runs-september.csv", new BigDecimal("600.00"), 3,
                List.of(
                        new BulkFileService.RawLine(1, "20301001,30992210,20-00-00,100.00," + today() + ",Stationery order 1182"),
                        new BulkFileService.RawLine(2, "20301001,30992212,20-00-00,200.00," + today() + ",Utilities Q3 top-up"),
                        new BulkFileService.RawLine(3, "20301002,30992211,20-00-00,300.00," + today() + ",Fit-out materials")));
        f1.setRef("BULK-ACM-0001");
        bulkFileRepository.save(f1);
        bulkFileService.process(f1);

        // f2: two valid lines, two invalid (unknown beneficiary / cooling beneficiary).
        BulkFile f2 = bulkFileService.upload(maker, "mixed-batch-september.csv", new BigDecimal("750.00"), 4,
                List.of(
                        new BulkFileService.RawLine(1, "20301001,30992210,20-00-00,250.00," + today() + ",Steel delivery"),
                        new BulkFileService.RawLine(2, "20301001,99999999,20-00-00,300.00," + today() + ",Unknown supplier"),
                        new BulkFileService.RawLine(3, "20301002,30992212,20-00-00,120.00," + today() + ",Utilities charge"),
                        new BulkFileService.RawLine(4, "20301001,30992213,20-00-00,80.00," + today() + ",Courier retainer")));
        f2.setRef("BULK-ACM-0002");
        bulkFileRepository.save(f2);
        bulkFileService.process(f2);

        // f3: declared total mismatch → whole file rejected.
        BulkFile f3 = bulkFileService.upload(maker, "mismatched-totals.csv", new BigDecimal("500.00"), 2,
                List.of(
                        new BulkFileService.RawLine(1, "20301001,30992210,20-00-00,250.00," + today() + ",Line A"),
                        new BulkFileService.RawLine(2, "20301001,30992212,20-00-00,200.00," + today() + ",Line B")));
        f3.setRef("BULK-ACM-0003");
        bulkFileRepository.save(f3);
        bulkFileService.process(f3);

        // f4: left UPLOADED — the batch module processes it on its first scheduled run (live demo).
        BulkFile f4 = bulkFileService.upload(maker, "overnight-batch.csv", new BigDecimal("760.00"), 2,
                List.of(
                        new BulkFileService.RawLine(1, "20301001,30992210,20-00-00,640.00," + today() + ",Overnight run 1"),
                        new BulkFileService.RawLine(2, "20301001,30992213,20-00-00,120.00," + today() + ",Cooling beneficiary")));
        f4.setRef("BULK-ACM-0004");
        bulkFileRepository.save(f4);
    }

    // ------------------------------------------------------------------ payments

    private void seedPayments() {
        // Each entry creates a DRAFT, submits it (workflow gate) and returns the payment for
        // per-status processing below.
        Payment p1001 = submitted("PY-ACM-1001", "1250.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Steel stock payment", 1, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1002", "4200.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992211"),
                "Q3 office fit-out deposit", 2, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1003", "780.50", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Retainer invoice", 3, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1004", "500.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Window cleaning August", 0, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1005", "1250.00", acct(SeedRefs.ACCOUNT_ACME_PAYROLL), ben("30992212"),
                "Utilities Q3", 1, maker(SeedRefs.USER_ACME_MAKER2));
        submitted("PY-ACM-1006", "30000.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992211"),
                "Fit-out phase 2", 0, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1007", "9000.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Equipment deposit", 2, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1008", "2750.00", acct(SeedRefs.ACCOUNT_ACME_PAYROLL), ben("51230456"),
                "Supplier advance (EUR)", 1, maker(SeedRefs.USER_ACME_MAKER2));
        Payment p1009 = submitted("PY-ACM-1009", "40000.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992211"),
                "Fit-out phase 3", 1, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1010", "1250.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Steel delivery June", 1, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1011", "340.50", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992211"),
                "Chair order 2211", 1, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1012", "2180.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992212"),
                "Energy bill July", 2, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1013", "1500.00", acct(SeedRefs.ACCOUNT_ACME_PAYROLL), ben("51230456"),
                "Nordic Trade invoice 8841", 2, maker(SeedRefs.USER_ACME_MAKER2));
        Payment p1014 = submitted("PY-ACM-1014", "980.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Harrow steel retainer", 2, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1015", "610.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992211"),
                "Desk screens", 2, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1016", "3300.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992212"),
                "Utilities September", 1, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1017", "1850.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Steel top-up", 0, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1018", "450.00", acct(SeedRefs.ACCOUNT_ACME_LOWBAL), ben("30992211"),
                "Small supplier test", 0, maker(SeedRefs.USER_ACME_MAKER1));
        submitted("PY-ACM-1019", "5600.00", acct(SeedRefs.ACCOUNT_ACME_OPS), ben("30992210"),
                "Next-month materials", 1, maker(SeedRefs.USER_ACME_MAKER1));

        submitted("PY-BRG-1001", "8400.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123876"),
                "Store refit deposit", 2, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1002", "12500.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123876"),
                "August stock purchase", 0, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1003", "96000.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123877"),
                "Marketing campaign Q3", 1, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1004", "22000.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123877"),
                "Campaign phase 1", 0, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1005", "1200.00", acct(SeedRefs.ACCOUNT_BRIGHTON_FLOAT), ben("40123877"),
                "Duplicate media buy", 3, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1006", "9200.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123876"),
                "Coastal wholesale stock", 0, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1010", "12500.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123876"),
                "July stock payment", 1, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1011", "4750.25", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123877"),
                "June campaign", 2, maker(SeedRefs.USER_BRIGHTON_MAKER));
        submitted("PY-BRG-1012", "25000.00", acct(SeedRefs.ACCOUNT_BRIGHTON_TRADING), ben("40123876"),
                "May bulk stock", 3, maker(SeedRefs.USER_BRIGHTON_MAKER));

        submitted("PY-CRF-1001", "210000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_MAIN), ben("41099877"),
                "Container fleet lease", 0, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1002", "480000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_MAIN), ben("74322011"),
                "Euro supplier settlement", 1, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1003", "75000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_COLLECTIONS), ben("41099878"),
                "Harbour dues", 0, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1004", "12400.00", acct(SeedRefs.ACCOUNT_CRANFIELD_COLLECTIONS), ben("41099877"),
                "Payroll run", 2, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1005", "33000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_TREASURY), ben("41099878"),
                "Berth maintenance", 3, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1010", "48000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_MAIN), ben("41099877"),
                "Fleet fuel July", 1, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1011", "8340.00", acct(SeedRefs.ACCOUNT_CRANFIELD_COLLECTIONS), ben("41099878"),
                "Marine services June", 2, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1012", "60000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_MAIN), ben("74322011"),
                "Euro Logistics June", 2, maker(SeedRefs.USER_CRANFIELD_MAKER));
        submitted("PY-CRF-1013", "99000.00", acct(SeedRefs.ACCOUNT_CRANFIELD_TREASURY), ben("41099877"),
                "Atlas freight May", 4, maker(SeedRefs.USER_CRANFIELD_MAKER));

        submitted("PY-DEL-1001", "88000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("52210034"),
                "Turbine spares", 1, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1002", "1200000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("90211045"),
                "Wind farm components (EUR)", 1, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1003", "245000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("52210034"),
                "Grid components", 0, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1004", "750000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("065544210"),
                "Exchange settlement (USD)", 2, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1005", "15000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("52210034"),
                "Missing purchase order", 3, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1010", "250000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("52210034"),
                "July components", 1, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1011", "180000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("90211045"),
                "Stratos June (EUR)", 2, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1012", "95000.00", acct(SeedRefs.ACCOUNT_DELOS_CAPITAL), ben("065544210"),
                "Exchange May (USD)", 3, maker(SeedRefs.USER_DELOS_MAKER));
        submitted("PY-DEL-1013", "310000.00", acct(SeedRefs.ACCOUNT_DELOS_OPERATING), ben("52210034"),
                "IonGrid April", 5, maker(SeedRefs.USER_DELOS_MAKER));

        // ---- approvals (through the real ApprovalService) ----
        approve("PY-ACM-1001", SeedRefs.USER_ACME_CHECKER1, null);      // → approved (single)
        approve("PY-ACM-1009", SeedRefs.USER_ACME_CHECKER1, "First approval of dual path"); // → partially_approved
        approve("PY-ACM-1010", SeedRefs.USER_ACME_CHECKER1, null);
        approve("PY-ACM-1011", SeedRefs.USER_ACME_CHECKER1, null);
        approve("PY-ACM-1012", SeedRefs.USER_ACME_CHECKER2, null);
        approve("PY-ACM-1013", SeedRefs.USER_ACME_CHECKER1, null);
        approve("PY-ACM-1014", SeedRefs.USER_ACME_CHECKER2, null);
        approve("PY-ACM-1017", SeedRefs.USER_ACME_CHECKER1, "Approved for release today");
        approve("PY-ACM-1018", SeedRefs.USER_ACME_CHECKER1, null);
        approve("PY-ACM-1019", SeedRefs.USER_ACME_CHECKER1, null);
        reject("PY-ACM-1015", SeedRefs.USER_ACME_CHECKER2, "Beneficiary details need updating");
        returnForAmendment("PY-ACM-1016", SeedRefs.USER_ACME_CHECKER1, "Please attach the supporting invoice");

        approve("PY-BRG-1004", SeedRefs.USER_BRIGHTON_CHECKER, null);
        reject("PY-BRG-1005", SeedRefs.USER_BRIGHTON_CHECKER, "Duplicate of an earlier payment");
        approve("PY-BRG-1006", SeedRefs.USER_BRIGHTON_CHECKER, null);
        approve("PY-BRG-1010", SeedRefs.USER_BRIGHTON_CHECKER, null);
        approve("PY-BRG-1011", SeedRefs.USER_BRIGHTON_CHECKER, null);
        approve("PY-BRG-1012", SeedRefs.USER_BRIGHTON_CHECKER, null);

        approve("PY-CRF-1003", SeedRefs.USER_CRANFIELD_CHECKER, null);
        returnForAmendment("PY-CRF-1004", SeedRefs.USER_CRANFIELD_CHECKER, "Wrong value date for payroll run");
        approve("PY-CRF-1010", SeedRefs.USER_CRANFIELD_CHECKER, null);
        approve("PY-CRF-1011", SeedRefs.USER_CRANFIELD_CHECKER, null);
        approve("PY-CRF-1012", SeedRefs.USER_CRANFIELD_CHECKER, null);
        approve("PY-CRF-1013", SeedRefs.USER_CRANFIELD_CHECKER, null);

        approve("PY-DEL-1001", SeedRefs.USER_DELOS_CHECKER, null);
        approve("PY-DEL-1003", SeedRefs.USER_DELOS_CHECKER, null);
        reject("PY-DEL-1005", SeedRefs.USER_DELOS_CHECKER, "Company policy: attach purchase order");
        approve("PY-DEL-1010", SeedRefs.USER_DELOS_CHECKER, null);
        approve("PY-DEL-1011", SeedRefs.USER_DELOS_CHECKER, null);
        approve("PY-DEL-1012", SeedRefs.USER_DELOS_CHECKER, null);
        approve("PY-DEL-1013", SeedRefs.USER_DELOS_CHECKER, null);

        // Bulk file f1: approve and release each line payment.
        for (BulkFileLine line : lineRepository.findByBulkFileIdOrderByLineNumberAsc(bulk("BULK-ACM-0001").getId())) {
            Payment p = line.getPayment();
            if (p != null) {
                approve(p.getRef(), SeedRefs.USER_ACME_CHECKER1, null);
                released(p.getRef(), "CORE-" + p.getRef(), 1);
            }
        }
        // Bulk file f2: approve and release the two valid lines.
        for (BulkFileLine line : lineRepository.findByBulkFileIdOrderByLineNumberAsc(bulk("BULK-ACM-0002").getId())) {
            Payment p = line.getPayment();
            if (p != null) {
                approve(p.getRef(), SeedRefs.USER_ACME_CHECKER2, null);
                released(p.getRef(), "CORE-" + p.getRef(), 1);
            }
        }

        // ---- releases (simulated core outcomes with deterministic core references) ----
        released("PY-ACM-1010", "CORE-PY-ACM-1010", 1);
        released("PY-ACM-1011", "CORE-PY-ACM-1011", 1);
        released("PY-ACM-1012", "CORE-PY-ACM-1012", 2);
        released("PY-ACM-1013", "CORE-PY-ACM-1013", 2);
        released("PY-ACM-1014", "CORE-PY-ACM-1014", 2);
        released("PY-BRG-1010", "CORE-PY-BRG-1010", 1);
        released("PY-BRG-1011", "CORE-PY-BRG-1011", 2);
        released("PY-BRG-1012", "CORE-PY-BRG-1012", 3);
        released("PY-CRF-1010", "CORE-PY-CRF-1010", 1);
        released("PY-CRF-1011", "CORE-PY-CRF-1011", 2);
        released("PY-CRF-1012", "CORE-PY-CRF-1012", 2);
        released("PY-CRF-1013", "CORE-PY-CRF-1013", 4);
        released("PY-DEL-1010", "CORE-PY-DEL-1010", 1);
        released("PY-DEL-1011", "CORE-PY-DEL-1011", 2);
        released("PY-DEL-1012", "CORE-PY-DEL-1012", 3);
        released("PY-DEL-1013", "CORE-PY-DEL-1013", 5);

        // Two FAILED releases: the core declined with response code 91 (forced failure); the
        // forced_failure rows remain in core-mock with one attempt each for the retry journey.
        failed("PY-ACM-1001", "91", "forced_failure", 1);
        failed("PY-DEL-1001", "91", "forced_failure", 1);

        // One queued-for-release payment (the batch module completes it on startup).
        queue("PY-BRG-1006");

        // Ops investigation demo on the Delos failure.
        Release delosRelease = releaseRepository.findByPaymentId(payment("PY-DEL-1001").getId()).orElseThrow();
        delosRelease.setInvestigationStatus(ReleaseInvestigationStatus.INVESTIGATING);
        delosRelease.setInvestigationNotes("Forced decline under investigation with the core team");
        releaseRepository.save(delosRelease);

        // Backdate entity timestamps for realistic dashboards.
        backdate();
    }

    private Payment submitted(String ref, String amount, Account account, Beneficiary beneficiary,
                              String reference, int valueDateOffsetDays, CompanyUser maker) {
        Payment payment = new Payment(UUID.randomUUID(), ref, account.getCompany(), account, beneficiary,
                new BigDecimal(amount), account.getCurrency(),
                LocalDate.now(ZoneOffset.UTC).plusDays(valueDateOffsetDays),
                reference, PaymentStatus.DRAFT, maker);
        workflow.applyPricing(payment, Instant.now());
        paymentRepository.save(payment);
        workflow.transition(payment, PaymentStatus.PENDING_APPROVAL, principal(maker),
                "PAYMENT_SUBMITTED", "Payment submitted for approval", "maker_submit");
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private void approve(String ref, UUID checkerId, String comment) {
        approvalService.approve(principal(user(checkerId)), payment(ref).getId(), comment);
    }

    private void reject(String ref, UUID checkerId, String comment) {
        approvalService.reject(principal(user(checkerId)), payment(ref).getId(), comment);
    }

    private void returnForAmendment(String ref, UUID checkerId, String comment) {
        approvalService.returnForAmendment(principal(user(checkerId)), payment(ref).getId(), comment);
    }

    /**
     * Simulates the core posting outcome for a released payment without a network call (the core
     * stub seeds the matching postings itself). The payment still passes through the same state
     * transitions (approved→queued_for_release→released) with audit events, exactly as a live
     * release would.
     */
    private void released(String ref, String coreRef, int daysAgo) {
        Payment payment = payment(ref);
        workflow.transitionSystem(payment, PaymentStatus.QUEUED_FOR_RELEASE, "seed-release",
                "PAYMENT_QUEUED", "Payment queued for release", "seed_release");
        workflow.transitionSystem(payment, PaymentStatus.RELEASED, "seed-release",
                "PAYMENT_RELEASED", "Payment posted to core as " + coreRef, "core_posting ref=" + coreRef);
        workflow.applyPricing(payment, Instant.now());
        Release release = new Release(UUID.randomUUID(), payment, payment.debitTotal());
        release.setCoreRef(coreRef);
        release.setResponseCode("00");
        release.setReleasedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        release.setAttemptedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        releaseRepository.save(release);
        bulkFileService.onPaymentReleased(payment);
    }

    private void failed(String ref, String responseCode, String reason, int retryCount) {
        Payment payment = payment(ref);
        workflow.transitionSystem(payment, PaymentStatus.QUEUED_FOR_RELEASE, "seed-release",
                "PAYMENT_QUEUED", "Payment queued for release", "seed_release");
        workflow.transitionSystem(payment, PaymentStatus.FAILED, "seed-release",
                "PAYMENT_RELEASE_FAILED", "Release failed: " + reason, "core_decline code=" + responseCode);
        payment.setReason(reason);
        paymentRepository.save(payment);
        workflow.applyPricing(payment, Instant.now());
        Release release = new Release(UUID.randomUUID(), payment, payment.debitTotal());
        release.setResponseCode(responseCode);
        release.setReason(reason);
        release.setRetryCount(retryCount);
        release.setAttemptedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        releaseRepository.save(release);
    }

    private void queue(String ref) {
        Payment payment = payment(ref);
        workflow.transitionSystem(payment, PaymentStatus.QUEUED_FOR_RELEASE, "seed-release",
                "PAYMENT_QUEUED", "Payment queued for release", "seed_release");
        paymentRepository.save(payment);
    }

    // ------------------------------------------------------------------ reversals

    private void seedReleasesAndReversals() {
        // RV1: fully posted reversal on PY-ACM-1014 (headroom restored).
        Payment p1014 = payment("PY-ACM-1014");
        Reversal rv1 = reversalService.request(principal(user(SeedRefs.USER_OPS1)), p1014.getId(),
                "Customer reported the payment was sent in error");
        Reversal rv1Approved = reversalService.decide(principal(user(SeedRefs.USER_BANK_ADMIN)), rv1.getId(), true);
        simulateReversalPost(rv1Approved, "CORE-REV-PY-ACM-1014", 1);

        // RV2: requested, awaiting bank_admin decision (UI demo).
        reversalService.request(principal(user(SeedRefs.USER_OPS2)),
                payment("PY-BRG-1010").getId(), "Customer disputes the beneficiary details");

        // RV3: approved, awaiting posting — the batch module posts it on startup.
        Reversal rv3 = reversalService.request(principal(user(SeedRefs.USER_OPS1)),
                payment("PY-CRF-1011").getId(), "Duplicate release identified by reconciliation review");
        reversalService.decide(principal(user(SeedRefs.USER_BANK_ADMIN)), rv3.getId(), true);
    }

    /** Simulated core outcome for the reversal posting (the same transitions a live posting makes). */
    private void simulateReversalPost(Reversal reversal, String coreRef, int daysAgo) {
        Payment payment = reversal.getPayment();
        PaymentStateMachine.require(payment.getStatus(), PaymentStatus.REVERSED);
        payment.setStatus(PaymentStatus.REVERSED);
        payment.setUpdatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        paymentRepository.save(payment);
        ReversalStateMachine.require(reversal.getStatus(), ReversalStatus.POSTED);
        reversal.setStatus(ReversalStatus.POSTED);
        reversal.setCoreRef(coreRef);
        reversal.setPostedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        reversalRepository.save(reversal);
        auditService.auditSystemForCompany("seed-release", payment.getCompany().getId(), "REVERSAL_POSTED",
                "reversal", reversal.getId(), payment.getRef(),
                "Reversal posted to core as " + coreRef,
                Map.of("status", "approved", "paymentStatus", "released"),
                Map.of("status", "posted", "paymentStatus", "reversed"),
                "system=seed basis=reversal_posting headroom=restored");
    }

    // ------------------------------------------------------------------ notifications & webhooks

    private void seedNotificationsAndWebhooks() {
        notify(SeedRefs.USER_ACME_CHECKER1, NotificationType.APPROVAL_PENDING,
                "Payment PY-ACM-1006 (30000.00 GBP) is awaiting approval — dual-approval path", "/payments");
        notify(SeedRefs.USER_ACME_CHECKER1, NotificationType.APPROVAL_PENDING,
                "Payment PY-ACM-1009 (40000.00 GBP) needs a second approval", "/payments");
        notify(SeedRefs.USER_ACME_CHECKER2, NotificationType.APPROVAL_PENDING,
                "Payment PY-ACM-1007 (9000.00 GBP) is awaiting approval", "/payments");
        notify(SeedRefs.USER_ACME_MAKER1, NotificationType.PAYMENT_RETURNED,
                "Payment PY-ACM-1016 was returned for amendment", "/payments");
        notify(SeedRefs.USER_ACME_MAKER1, NotificationType.RELEASE_FAILED,
                "Release of payment PY-ACM-1001 failed (forced_failure)", "/payments");
        notify(SeedRefs.USER_DELOS_MAKER, NotificationType.RELEASE_FAILED,
                "Release of payment PY-DEL-1001 failed (forced_failure)", "/payments");
        notify(SeedRefs.USER_OPS1, NotificationType.RELEASE_FAILED,
                "Release of payment PY-ACM-1001 was declined by core (forced failure)", "/ops/releases");
        notify(SeedRefs.USER_OPS2, NotificationType.RELEASE_FAILED,
                "Release of payment PY-DEL-1001 was declined by core (forced failure)", "/ops/releases");
        notify(SeedRefs.USER_BANK_ADMIN, NotificationType.REVERSAL_REQUESTED,
                "Reversal requested for payment PY-BRG-1010: customer disputes beneficiary details",
                "/admin/reversals");
        notify(SeedRefs.USER_BRIGHTON_CHECKER, NotificationType.APPROVAL_PENDING,
                "Payment PY-BRG-1003 (96000.00 GBP) is awaiting approval", "/payments");
        notify(SeedRefs.USER_CRANFIELD_CHECKER, NotificationType.APPROVAL_PENDING,
                "Payment PY-CRF-1001 (210000.00 GBP) is awaiting approval", "/payments");
        notify(SeedRefs.USER_DELOS_CHECKER, NotificationType.APPROVAL_PENDING,
                "Payment PY-DEL-1004 (750000.00 GBP) is awaiting approval", "/payments");

        // Webhook config: Cranfield active (with recorded deliveries), Brighton inactive.
        WebhookConfig cranfield = new WebhookConfig(UUID.randomUUID(),
                companyRepository.findById(SeedRefs.COMPANY_CRANFIELD).orElseThrow(),
                "https://hooks.example.com/cranfield/payments", "whsec_demo_secret", true);
        webhookConfigRepository.save(cranfield);
        webhookConfigRepository.save(new WebhookConfig(UUID.randomUUID(),
                companyRepository.findById(SeedRefs.COMPANY_BRIGHTON).orElseThrow(),
                "https://hooks.example.com/brighton/payments", "whsec_demo_secret", false));

        recordWebhookDelivery("PY-CRF-1010", cranfield, "MOCKED_DELIVERED", "200", 1);
        recordWebhookDelivery("PY-CRF-1011", cranfield, "MOCKED_DELIVERED", "200", 2);
    }

    private void recordWebhookDelivery(String paymentRef, WebhookConfig config, String status,
                                       String responseCode, int daysAgo) {
        Payment payment = payment(paymentRef);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "payment.released");
        payload.put("paymentRef", payment.getRef());
        payload.put("amount", payment.getAmount());
        WebhookDelivery delivery = new WebhookDelivery(UUID.randomUUID(), payment, payment.getCompany(),
                config.getUrl(), payload);
        delivery.setStatus(status);
        delivery.setResponseCode(responseCode);
        delivery.setAttempts(1);
        delivery.setDeliveredAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        webhookDeliveryRepository.save(delivery);
    }

    private void notify(UUID userId, NotificationType type, String message, String link) {
        notifications.notify(userId, type, message, link);
    }

    // ------------------------------------------------------------------ helpers

    private void backdate() {
        Instant now = Instant.now();
        paymentRepository.findAll().forEach(p -> {
            int days = switch (p.getRef()) {
                case "PY-ACM-1010", "PY-ACM-1011", "PY-BRG-1010", "PY-CRF-1010", "PY-DEL-1010" -> 1;
                case "PY-ACM-1012", "PY-ACM-1013", "PY-ACM-1014", "PY-BRG-1011", "PY-CRF-1011",
                     "PY-CRF-1012", "PY-DEL-1011" -> 2;
                case "PY-BRG-1012", "PY-CRF-1013", "PY-DEL-1012" -> 3;
                case "PY-DEL-1013" -> 5;
                default -> 0;
            };
            if (days > 0) {
                p.setCreatedAt(now.minus(days, ChronoUnit.DAYS));
                p.setUpdatedAt(now.minus(days, ChronoUnit.DAYS));
            }
            paymentRepository.save(p);
        });
    }

    private Payment payment(String ref) {
        return paymentRepository.findByRef(ref).orElseThrow(() -> new IllegalStateException("missing " + ref));
    }

    private BulkFile bulk(String ref) {
        return bulkFileRepository.findByRef(ref).orElseThrow(() -> new IllegalStateException("missing " + ref));
    }

    private CompanyUser user(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalStateException("missing user " + id));
    }

    private Account acct(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new IllegalStateException("missing account"));
    }

    private Beneficiary ben(String accountNumber) {
        // Beneficiaries are unique per company+account; pick the Acme one for these seeds.
        return beneficiaryRepository.findByCompanyIdAndAccountNumberAndBankCode(
                        SeedRefs.COMPANY_ACME, accountNumber, accountNumber.startsWith("5") ? "SEB-500" : "20-00-00")
                .or(() -> beneficiaryRepository.findByCompanyIdAndAccountNumberAndBankCode(
                        SeedRefs.COMPANY_BRIGHTON, accountNumber, "20-00-00"))
                .or(() -> beneficiaryRepository.findByCompanyIdAndAccountNumberAndBankCode(
                        SeedRefs.COMPANY_CRANFIELD, accountNumber, accountNumber.startsWith("7") ? "DEUTDEFF" : "20-00-00"))
                .or(() -> beneficiaryRepository.findByCompanyIdAndAccountNumberAndBankCode(
                        SeedRefs.COMPANY_DELOS, accountNumber, accountNumber.startsWith("9") ? "DEUTDEFF"
                                : accountNumber.startsWith("0") ? "CHASUS33" : "20-00-00"))
                .orElseThrow(() -> new IllegalStateException("missing beneficiary " + accountNumber));
    }

    private CompanyUser maker(UUID id) {
        return user(id);
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private AuthPrincipal principal(CompanyUser user) {
        return new AuthPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getCompany() == null ? null : user.getCompany().getId(),
                user.getApprovalLimit(),
                user.getAssignedAccounts().stream().map(BaseEntity::getId).collect(Collectors.toSet()),
                false);
    }
}
