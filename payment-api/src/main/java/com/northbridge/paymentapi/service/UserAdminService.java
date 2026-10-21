package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.AccountRepository;
import com.northbridge.paymentapi.repo.CompanyRepository;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Company user administration (company_admin). Role and approval-limit assignments are validated
 * server-side: no user's limit may exceed the company limit, and an admin can never change their
 * own role (so they cannot grant themselves checker rights) nor disable themselves.
 */
@Service
public class UserAdminService {

    private final CompanyUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final AccessControlService access;
    private final AuditService auditService;
    private final NotificationService notifications;

    public UserAdminService(CompanyUserRepository userRepository,
                            CompanyRepository companyRepository,
                            AccountRepository accountRepository,
                            AccessControlService access,
                            AuditService auditService,
                            NotificationService notifications) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.accountRepository = accountRepository;
        this.access = access;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<CompanyUser> listCompanyUsers(AuthPrincipal actor) {
        requireCompanyAdmin(actor);
        return userRepository.findByCompanyIdOrderByFullNameAsc(actor.companyId());
    }

    /** Creates an INVITED user; the portal tier owns the invite token and acceptance flow. */
    @Transactional
    public CompanyUser invite(AuthPrincipal actor, String email, String fullName, Role role,
                              BigDecimal approvalLimit, List<UUID> accountIds) {
        requireCompanyAdmin(actor);
        Company company = company(actor.companyId());
        access.requireActiveCompany(company);
        String cleanEmail = cleanEmail(email);
        String cleanName = cleanName(fullName);
        validateRole(role);
        BigDecimal limit = limitFor(role, approvalLimit, company);
        Set<Account> accounts = validateAccounts(actor.companyId(), accountIds);

        if (userRepository.existsByCompanyIdAndEmailIgnoreCase(company.getId(), cleanEmail)) {
            throw new ApiException(ApiException.Code.DUPLICATE, "A user with this email already exists");
        }
        CompanyUser user = new CompanyUser(UUID.randomUUID(), cleanEmail, cleanName, role,
                UserStatus.INVITED, limit, company);
        user.setAssignedAccounts(accounts);
        userRepository.save(user);
        auditService.audit(actor, "USER_INVITED", "user", user.getId(), null,
                "Invited " + cleanEmail + " as " + role.apiName(),
                null, Map.of("role", role.apiName(), "approvalLimit", limit));
        return user;
    }

    @Transactional
    public CompanyUser update(AuthPrincipal actor, UUID userId, String fullName, Role role,
                              BigDecimal approvalLimit, List<UUID> accountIds) {
        requireCompanyAdmin(actor);
        Company company = company(actor.companyId());
        CompanyUser target = requireCompanyMember(actor, userId);
        if (target.getId().equals(actor.userId())) {
            throw new ApiException(ApiException.Code.SELF_ACTION_FORBIDDEN,
                    "You cannot change your own role or approval limit");
        }
        Map<String, Object> before = Map.of(
                "fullName", target.getFullName(), "role", target.getRole().apiName(),
                "approvalLimit", target.getApprovalLimit());
        Map<String, Object> after = new LinkedHashMap<>();

        if (fullName != null && !fullName.isBlank() && !fullName.equals(target.getFullName())) {
            target.setFullName(cleanName(fullName));
            after.put("fullName", target.getFullName());
        }
        if (role != null && role != target.getRole()) {
            validateRole(role);
            target.setRole(role);
            target.setApprovalLimit(limitFor(role, approvalLimit, company));
            after.put("role", role.apiName());
            after.put("approvalLimit", target.getApprovalLimit());
        } else if (approvalLimit != null) {
            target.setApprovalLimit(limitFor(target.getRole(), approvalLimit, company));
            after.put("approvalLimit", target.getApprovalLimit());
        }
        if (accountIds != null) {
            Set<Account> accounts = validateAccounts(actor.companyId(), accountIds);
            target.setAssignedAccounts(accounts);
            after.put("accounts", accountIds.stream().map(UUID::toString).collect(Collectors.toList()));
        }
        if (after.isEmpty()) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "No changes supplied");
        }
        target.setUpdatedAt(java.time.Instant.now());
        userRepository.save(target);
        auditService.audit(actor, "USER_UPDATED", "user", target.getId(), null,
                "Updated user " + target.getEmail(), before, after);
        if (after.containsKey("role")) {
            notifications.notify(target.getId(), NotificationType.USER_ROLE_CHANGED,
                    "Your role changed to " + target.getRole().apiName(), "/profile");
        }
        return target;
    }

    @Transactional
    public CompanyUser setStatus(AuthPrincipal actor, UUID userId, UserStatus status) {
        requireCompanyAdmin(actor);
        if (userId.equals(actor.userId())) {
            throw new ApiException(ApiException.Code.SELF_ACTION_FORBIDDEN,
                    "You cannot disable your own account");
        }
        CompanyUser target = requireCompanyMember(actor, userId);
        if (status != UserStatus.DISABLED && status != UserStatus.ACTIVE) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Invalid status change");
        }
        if (target.getStatus() == status) {
            return target;
        }
        Map<String, Object> before = Map.of("status", target.getStatus().name());
        target.setStatus(status);
        target.setUpdatedAt(java.time.Instant.now());
        userRepository.save(target);
        auditService.audit(actor, status == UserStatus.DISABLED ? "USER_DISABLED" : "USER_ENABLED",
                "user", target.getId(), null,
                (status == UserStatus.DISABLED ? "Disabled " : "Enabled ") + target.getEmail(),
                before, Map.of("status", status.name()));
        return target;
    }

    /** Portal acceptance of an invite: INVITED→ACTIVE (system action, portal-verified invite). */
    @Transactional
    public CompanyUser activate(UUID userId) {
        CompanyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.INVITED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "User is not awaiting invite acceptance");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(java.time.Instant.now());
        userRepository.save(user);
        auditService.auditSystemForCompany("portal", user.getCompany().getId(), "USER_ACTIVATED",
                "user", user.getId(), null,
                "Invite accepted by " + user.getEmail(),
                Map.of("status", "invited"), Map.of("status", "active"),
                "system=portal basis=invite_acceptance");
        return user;
    }

    // ------------------------------------------------------------------ helpers

    private void requireCompanyAdmin(AuthPrincipal actor) {
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN,
                    "Only company_admin manages company users");
        }
    }

    private CompanyUser requireCompanyMember(AuthPrincipal actor, UUID userId) {
        CompanyUser target = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "User not found"));
        if (!target.getCompany().getId().equals(actor.companyId())) {
            throw new ApiException(ApiException.Code.NOT_FOUND, "User not found");
        }
        return target;
    }

    private Company company(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
    }

    private void validateRole(Role role) {
        if (role != Role.COMPANY_MAKER && role != Role.COMPANY_CHECKER && role != Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Role must be one of company_maker, company_checker, company_admin");
        }
    }

    /** Approval limits are always validated against the company limit, server-side. */
    private BigDecimal limitFor(Role role, BigDecimal requested, Company company) {
        if (role != Role.COMPANY_CHECKER) {
            return BigDecimal.ZERO;
        }
        if (requested == null || requested.signum() < 0) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Approval limit is required for checkers");
        }
        BigDecimal limit = MoneyUtil.round(requested, company.getBaseCurrency());
        if (limit.compareTo(company.getDailyLimit()) > 0) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Approval limit (" + limit + ") cannot exceed the company daily limit ("
                            + company.getDailyLimit() + " " + company.getBaseCurrency() + ")");
        }
        return limit;
    }

    private Set<Account> validateAccounts(UUID companyId, List<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return new HashSet<>(); // empty = unrestricted (all company accounts)
        }
        Set<Account> accounts = new HashSet<>();
        for (UUID id : new HashSet<>(accountIds)) {
            Account account = accountRepository.findById(id).orElse(null);
            if (account == null || !account.getCompany().getId().equals(companyId)) {
                throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                        "Account " + id + " does not belong to this company");
            }
            accounts.add(account);
        }
        return accounts;
    }

    private String cleanEmail(String email) {
        if (email == null || !email.matches("^[^@\\s]{1,64}@[^@\\s]{1,255}$")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "A valid email address is required");
        }
        return email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Full name is required (max 200)");
        }
        return name.strip();
    }
}
