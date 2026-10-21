package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.AccountRepository;
import com.northbridge.paymentapi.repo.AuditEventRepository;
import com.northbridge.paymentapi.repo.BeneficiaryRepository;
import com.northbridge.paymentapi.repo.CompanyRepository;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.repo.ReversalRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank-admin operations: company onboarding, company-level limits, account provisioning and the
 * global audit view. bank_admin can manage companies and limits and approve reversals, but can
 * NEVER approve a customer payment (that right belongs exclusively to company_checker).
 */
@Service
public class BankAdminService {

    private final CompanyRepository companyRepository;
    private final CompanyUserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ReversalRepository reversalRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AuditEventRepository auditEventRepository;
    private final LimitService limitService;
    private final CoreClient coreClient;
    private final AuditService auditService;
    private final NotificationService notifications;

    public BankAdminService(CompanyRepository companyRepository,
                            CompanyUserRepository userRepository,
                            AccountRepository accountRepository,
                            ReversalRepository reversalRepository,
                            BeneficiaryRepository beneficiaryRepository,
                            AuditEventRepository auditEventRepository,
                            LimitService limitService,
                            CoreClient coreClient,
                            AuditService auditService,
                            NotificationService notifications) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.reversalRepository = reversalRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.auditEventRepository = auditEventRepository;
        this.limitService = limitService;
        this.coreClient = coreClient;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    private void requireBankAdmin(AuthPrincipal actor) {
        if (actor.role() != Role.BANK_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only bank_admin can perform this action");
        }
    }

    // ------------------------------------------------------------------ companies

    @Transactional(readOnly = true)
    public List<Company> listCompanies(AuthPrincipal actor) {
        requireBankAdmin(actor);
        return companyRepository.findAll(Sort.by("legalName"));
    }

    @Transactional
    public Company createCompany(AuthPrincipal actor, String legalName, String registrationNumber,
                                 String baseCurrency, BigDecimal dailyLimit) {
        requireBankAdmin(actor);
        if (legalName == null || legalName.isBlank() || legalName.length() > 200) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Legal name is required");
        }
        if (registrationNumber == null || !registrationNumber.matches("[A-Z0-9\\-]{4,40}")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A valid registration number is required");
        }
        String currency = cleanCurrency(baseCurrency);
        BigDecimal limit = cleanLimit(dailyLimit, currency);
        if (companyRepository.existsByRegistrationNumberIgnoreCase(registrationNumber.strip())) {
            throw new ApiException(ApiException.Code.DUPLICATE, "A company with this registration number exists");
        }
        Company company = new Company(UUID.randomUUID(), legalName.strip(), registrationNumber.strip(),
                currency, limit);
        companyRepository.save(company);
        auditService.audit(actor, "COMPANY_ONBOARDED", "company", company.getId(), null,
                "Onboarded " + company.getLegalName(),
                null, Map.of("legalName", company.getLegalName(), "dailyLimit", limit,
                        "baseCurrency", currency));
        return company;
    }

    @Transactional
    public Company setDailyLimit(AuthPrincipal actor, UUID companyId, BigDecimal newLimit, String reason) {
        requireBankAdmin(actor);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        BigDecimal limit = cleanLimit(newLimit, company.getBaseCurrency());
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A reason is required (max 500)");
        }
        Map<String, Object> before = Map.of("dailyLimit", company.getDailyLimit());
        company.setDailyLimit(limit);
        companyRepository.save(company);
        auditService.audit(actor, "COMPANY_LIMIT_CHANGED", "company", company.getId(), null,
                "Daily limit changed to " + limit + " (" + reason.strip() + ")",
                before, Map.of("dailyLimit", limit),
                actor.authBasis() + " basis=bank_admin_limit_change reason=" + reason.strip());
        notifications.notifyCompanyRole(companyId, Role.COMPANY_ADMIN, NotificationType.USER_ROLE_CHANGED,
                "Your company daily limit is now " + limit + " " + company.getBaseCurrency(),
                "/company");
        return company;
    }

    @Transactional
    public CompanyUser inviteCompanyAdmin(AuthPrincipal actor, UUID companyId, String email, String fullName) {
        requireBankAdmin(actor);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        String cleanEmail = email == null || !email.matches("^[^@\\s]{1,64}@[^@\\s]{1,255}$")
                ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
        if (cleanEmail == null) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A valid email address is required");
        }
        if (fullName == null || fullName.isBlank() || fullName.length() > 200) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Full name is required");
        }
        if (userRepository.existsByCompanyIdAndEmailIgnoreCase(companyId, cleanEmail)) {
            throw new ApiException(ApiException.Code.DUPLICATE, "A user with this email already exists");
        }
        CompanyUser user = new CompanyUser(UUID.randomUUID(), cleanEmail, fullName.strip(),
                Role.COMPANY_ADMIN, UserStatus.INVITED, BigDecimal.ZERO, company);
        userRepository.save(user);
        auditService.audit(actor, "COMPANY_ADMIN_INVITED", "user", user.getId(), null,
                "Invited " + cleanEmail + " as company_admin for " + company.getLegalName(),
                null, Map.of("role", "company_admin"));
        return user;
    }

    /**
     * Confirms a proposed company-admin exists in the bank's staff directory before the invite is
     * sent, so onboarding never grants a company role to an unknown person.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyStaff(AuthPrincipal actor, String officerId) {
        requireBankAdmin(actor);
        if (officerId == null || officerId.isBlank() || officerId.length() > 64) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "An officer identifier is required");
        }
        String dn = "ou=staff,dc=northbridge,dc=bank";
        String filter = "(&(objectClass=person)(uid=" + officerId + "))";
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://directory.northbridge.internal:389");
        env.put("com.sun.jndi.ldap.connect.timeout", "2000");
        env.put("com.sun.jndi.ldap.read.timeout", "2000");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            SearchControls ctl = new SearchControls();
            ctl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctl.setCountLimit(1);
            NamingEnumeration<SearchResult> matches = ctx.search(dn, filter, ctl);
            if (matches.hasMore()) {
                Attribute cn = matches.next().getAttributes().get("cn");
                return Map.of("found", true, "name", cn == null ? officerId : String.valueOf(cn.get()));
            }
            return Map.of("found", false);
        } catch (NamingException e) {
            throw new ApiException(ApiException.Code.INTERNAL_ERROR, "Staff directory is unavailable");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // closing the directory context is best-effort
                }
            }
        }
    }

    /**
     * Directory search for the onboarding workbench: resolves a colleague by work email so the
     * administrator can pick the right person before the invite goes out.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchStaffDirectory(AuthPrincipal actor, String query) {
        requireBankAdmin(actor);
        if (query == null || query.isBlank() || query.length() > 64) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A search term is required");
        }
        String baseDn = "ou=staff,dc=northbridge,dc=bank";
        String filter = "(&(objectClass=person)(mail=" + query + "))";
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://directory.northbridge.internal:389");
        env.put("com.sun.jndi.ldap.connect.timeout", "2000");
        env.put("com.sun.jndi.ldap.read.timeout", "2000");
        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            SearchControls ctl = new SearchControls();
            ctl.setSearchScope(SearchControls.SUBTREE_SCOPE);
            ctl.setCountLimit(25);
            NamingEnumeration<SearchResult> matches = ctx.search(baseDn, filter, ctl);
            List<Map<String, Object>> people = new java.util.ArrayList<>();
            while (matches.hasMore()) {
                SearchResult match = matches.next();
                Attribute cn = match.getAttributes().get("cn");
                Attribute mail = match.getAttributes().get("mail");
                people.add(Map.of("name", cn == null ? "" : String.valueOf(cn.get()),
                        "email", mail == null ? "" : String.valueOf(mail.get())));
            }
            return people;
        } catch (NamingException e) {
            throw new ApiException(ApiException.Code.INTERNAL_ERROR, "Staff directory is unavailable");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // closing the directory context is best-effort
                }
            }
        }
    }

    /**
     * Bank override of a beneficiary's cooling period so a time-critical payment can proceed today.
     * The operator's note is passed on to the customer's administrators.
     */
    @Transactional
    public Beneficiary waiveCooling(AuthPrincipal actor, UUID beneficiaryId, String note) {
        requireBankAdmin(actor);
        if (note == null || note.isBlank() || note.length() > 500) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A reason is required (max 500)");
        }
        Beneficiary beneficiary = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found"));
        beneficiary.setStatus(BeneficiaryStatus.ACTIVE);
        beneficiary.setCoolingUntil(null);
        beneficiaryRepository.save(beneficiary);
        notifications.notifyCompanyRole(beneficiary.getCompany().getId(), Role.COMPANY_ADMIN,
                NotificationType.USER_ROLE_CHANGED,
                "Cooling period waived for " + beneficiary.getName() + ": " + note.strip(),
                "/beneficiaries");
        return beneficiary;
    }

    // ------------------------------------------------------------------ accounts

    @Transactional
    public Account createAccount(AuthPrincipal actor, UUID companyId, String accountNumber,
                                 String currency, String product, BigDecimal openingBalance) {
        requireBankAdmin(actor);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        if (accountNumber == null || !accountNumber.matches("[0-9]{8,20}")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Account number must be 8-20 digits");
        }
        if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            throw new ApiException(ApiException.Code.DUPLICATE, "An account with this number already exists");
        }
        String cleanCurrency = cleanCurrency(currency);
        if (product == null || product.isBlank() || product.length() > 32) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Product type is required");
        }
        if (openingBalance == null || openingBalance.signum() < 0) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Opening balance must be zero or positive");
        }
        Account account = new Account(UUID.randomUUID(), accountNumber, company, cleanCurrency,
                product.strip().toUpperCase(), product.strip());
        accountRepository.save(account);
        coreClient.provisionAccount(accountNumber, cleanCurrency, product, openingBalance, companyId);
        auditService.audit(actor, "ACCOUNT_PROVISIONED", "account", account.getId(), accountNumber,
                "Provisioned account " + accountNumber + " for " + company.getLegalName(),
                null, Map.of("currency", cleanCurrency, "openingBalance", openingBalance));
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> listAccountsForCompany(AuthPrincipal actor, UUID companyId) {
        requireBankAdmin(actor);
        return accountRepository.findByCompanyIdOrderByAccountNumberAsc(companyId);
    }

    // ------------------------------------------------------------------ audit + stats

    public record CompanyStats(Company company, LimitService.Utilisation utilisation,
                               long userCount, long pendingReversals) {
    }

    @Transactional(readOnly = true)
    public List<CompanyStats> companyStats(AuthPrincipal actor) {
        requireBankAdmin(actor);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return companyRepository.findAll(Sort.by("legalName")).stream()
                .map(c -> new CompanyStats(c, limitService.utilisation(c.getId(), c, today),
                        userRepository.findByCompanyIdOrderByFullNameAsc(c.getId()).size(),
                        reversalRepository.findByStatusOrderByRequestedAtAsc(ReversalStatus.REQUESTED)
                                .stream().filter(r -> r.getPayment().getCompany().getId().equals(c.getId())).count()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> auditPage(AuthPrincipal actor, String action, String resourceType,
                                      UUID companyId, Instant from, Instant to, int page, int size) {
        requireBankAdmin(actor);
        Specification<AuditEvent> spec = Specification.where(null);
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("action"), action));
        }
        if (resourceType != null && !resourceType.isBlank()) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("resourceType"), resourceType));
        }
        if (companyId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("companyId"), companyId));
        }
        if (from != null) {
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("eventAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("eventAt"), to));
        }
        int safeSize = Math.min(Math.max(size, 1), 200);
        return auditEventRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "eventAt")));
    }

    private String cleanCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Currency must be a 3-letter code");
        }
        return currency.toUpperCase();
    }

    private BigDecimal cleanLimit(BigDecimal limit, String currency) {
        if (limit == null || limit.signum() <= 0) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Daily limit must be positive");
        }
        return MoneyUtil.round(limit, currency);
    }
}
