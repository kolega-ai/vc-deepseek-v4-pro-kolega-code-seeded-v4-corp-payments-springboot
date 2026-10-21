package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.AccountRepository;
import com.northbridge.paymentapi.repo.CompanyRepository;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.BankAdminService;
import com.northbridge.paymentapi.service.LimitService;
import com.northbridge.paymentapi.service.ReversalService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.BankDtos;
import com.northbridge.paymentapi.web.dto.CompanyDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bank-admin operations: companies, limits, account provisioning, reversal decisions and the
 * global audit view. bank_admin can NEVER approve a customer payment.
 */
@RestController
@RequestMapping("/api/bank")
public class BankAdminController {

    private final BankAdminService bankAdminService;
    private final ReversalService reversalService;
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;
    private final LimitService limitService;
    private final ViewMapper mapper;

    public BankAdminController(BankAdminService bankAdminService,
                               ReversalService reversalService,
                               CompanyRepository companyRepository,
                               AccountRepository accountRepository,
                               LimitService limitService,
                               ViewMapper mapper) {
        this.bankAdminService = bankAdminService;
        this.reversalService = reversalService;
        this.companyRepository = companyRepository;
        this.accountRepository = accountRepository;
        this.limitService = limitService;
        this.mapper = mapper;
    }

    @GetMapping("/companies")
    public List<BankDtos.CompanyView> companies() {
        AuthPrincipal actor = AuthSupport.require();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return bankAdminService.listCompanies(actor).stream()
                .map(c -> mapper.company(c, limitService.utilisation(c.getId(), c, today), 0, 0))
                .toList();
    }

    @PostMapping("/companies")
    public BankDtos.CompanyView createCompany(@RequestBody Requests.CreateCompanyRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Company company = bankAdminService.createCompany(actor, request.legalName(),
                request.registrationNumber(), request.baseCurrency(), request.dailyLimit());
        return mapper.company(company, limitService.utilisationToday(company.getId(), company), 0, 0);
    }

    @GetMapping("/companies/{id}")
    public BankDtos.CompanyView company(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new com.northbridge.paymentapi.security.ApiException(
                        com.northbridge.paymentapi.security.ApiException.Code.NOT_FOUND, "Company not found"));
        return mapper.company(company, limitService.utilisationToday(company.getId(), company), 0, 0);
    }

    @PostMapping("/companies/{id}/limit")
    public BankDtos.CompanyView setLimit(@PathVariable UUID id,
                                         @RequestBody Requests.LimitChangeRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Company company = bankAdminService.setDailyLimit(actor, id, request.dailyLimit(), request.reason());
        return mapper.company(company, limitService.utilisationToday(company.getId(), company), 0, 0);
    }

    @PostMapping("/companies/{id}/admins/invite")
    public CompanyDtos.CompanyUserView inviteAdmin(@PathVariable UUID id,
                                                   @RequestBody Requests.InviteUserRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        CompanyUser user = bankAdminService.inviteCompanyAdmin(actor, id, request.email(),
                request.fullName());
        return mapper.user(user);
    }

    @PostMapping("/staff/verify")
    public Map<String, Object> verifyStaff(@RequestBody Requests.StaffVerifyRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        return bankAdminService.verifyStaff(actor, request.officerId());
    }

    @GetMapping("/staff/search")
    public List<Map<String, Object>> searchStaff(@RequestParam String query) {
        AuthPrincipal actor = AuthSupport.require();
        return bankAdminService.searchStaffDirectory(actor, query);
    }

    @PostMapping("/beneficiaries/{id}/cooling-waiver")
    public Map<String, Object> waiveCooling(@PathVariable UUID id,
                                            @RequestBody Requests.CoolingWaiverRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Beneficiary beneficiary = bankAdminService.waiveCooling(actor, id, request.reason());
        return Map.of("status", beneficiary.getStatus().name());
    }

    @GetMapping("/accounts")
    public List<CompanyDtos.AccountView> accounts(@RequestParam UUID companyId) {
        AuthPrincipal actor = AuthSupport.require();
        return bankAdminService.listAccountsForCompany(actor, companyId).stream()
                .map(a -> new CompanyDtos.AccountView(a.getId(), a.getAccountNumber(), a.getCurrency(),
                        a.getProduct(), a.getName(), null, a.getCompany().getId()))
                .toList();
    }

    @PostMapping("/accounts")
    public CompanyDtos.AccountView createAccount(@RequestBody Requests.CreateAccountRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Account account = bankAdminService.createAccount(actor, request.companyId(),
                request.accountNumber(), request.currency(), request.product(), request.openingBalance());
        return new CompanyDtos.AccountView(account.getId(), account.getAccountNumber(),
                account.getCurrency(), account.getProduct(), account.getName(), null,
                account.getCompany().getId());
    }

    @PostMapping("/reversals/{id}/approve")
    public com.northbridge.paymentapi.web.dto.PaymentDtos.ReversalView approveReversal(@PathVariable UUID id) {
        return mapper.reversal(reversalService.decide(AuthSupport.require(), id, true));
    }

    @PostMapping("/reversals/{id}/decline")
    public com.northbridge.paymentapi.web.dto.PaymentDtos.ReversalView declineReversal(@PathVariable UUID id) {
        return mapper.reversal(reversalService.decide(AuthSupport.require(), id, false));
    }

    @GetMapping("/audit")
    public Page<BankDtos.AuditView> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size) {
        AuthPrincipal actor = AuthSupport.require();
        return bankAdminService.auditPage(actor, action, resourceType, companyId, from, to, page, size)
                .map(mapper::audit);
    }
}
