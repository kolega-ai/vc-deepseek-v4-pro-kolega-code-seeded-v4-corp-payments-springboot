package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.BeneficiaryRepository;
import com.northbridge.paymentapi.repo.CompanyRepository;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.state.BeneficiaryStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Beneficiary management. New beneficiaries enter a 30-minute cooling period before first use;
 * amending the account number (or bank code) restarts the cooling period. Only ACTIVE beneficiaries
 * are payable, and pending_cooling→active happens lazily once cooling has elapsed.
 */
@Service
public class BeneficiaryService {

    public static final long COOLING_MINUTES = 30;

    private final BeneficiaryRepository repository;
    private final CompanyRepository companyRepository;
    private final CompanyUserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notifications;

    public BeneficiaryService(BeneficiaryRepository repository,
                              CompanyRepository companyRepository,
                              CompanyUserRepository userRepository,
                              AuditService auditService,
                              NotificationService notifications) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ list + etag

    @Transactional(readOnly = true)
    public List<Beneficiary> listForCompany(AuthPrincipal actor) {
        if (!actor.isCompanyUser()) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Company scope required");
        }
        return repository.findByCompanyIdOrderByNameAsc(actor.companyId());
    }

    /**
     * PRECISION LOCATION #3: MD5 used ONLY as an ETag for the beneficiary reference-data response.
     * This is a non-cryptographic use (cache validation of reference data); MD5 is intentionally
     * kept as required by the corpus contract and must not be substituted with SHA-256 or used for
     * any security purpose.
     */
    @Transactional(readOnly = true)
    public String etagForCompany(AuthPrincipal actor) {
        UUID companyId = actor.companyId();
        Instant maxUpdated = repository.maxUpdatedAtForCompany(companyId);
        String material = companyId + "|" + (maxUpdated == null ? "never" : maxUpdated.toString());
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5"); // non-cryptographic ETag
            return "\"" + HexFormat.of().formatHex(md5.digest(material.getBytes(StandardCharsets.UTF_8))) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ crud

    @Transactional
    public Beneficiary create(AuthPrincipal actor, String name, String accountNumber, String bankCode,
                              String currency) {
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_admin manages beneficiaries");
        }
        Company company = companyRepository.findById(actor.companyId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        String cleanName = cleanName(name);
        String cleanAccount = cleanAccountNumber(accountNumber);
        String cleanBankCode = cleanBankCode(bankCode);
        String cleanCurrency = cleanCurrency(currency);
        if (repository.findByCompanyIdAndAccountNumberAndBankCode(
                company.getId(), cleanAccount, cleanBankCode).isPresent()) {
            throw new ApiException(ApiException.Code.DUPLICATE, "A beneficiary with this account already exists");
        }
        CompanyUser creator = userRepository.findById(actor.userId()).orElse(null);
        Beneficiary beneficiary = new Beneficiary(UUID.randomUUID(), company, cleanName, cleanAccount,
                cleanBankCode, cleanCurrency, BeneficiaryStatus.PENDING_COOLING,
                Instant.now().plus(COOLING_MINUTES, ChronoUnit.MINUTES), creator);
        repository.save(beneficiary);
        auditService.audit(actor, "BENEFICIARY_CREATED", "beneficiary", beneficiary.getId(), null,
                "Beneficiary created, cooling until " + beneficiary.getCoolingUntil(),
                null, Map.of("status", "pending_cooling", "account", cleanAccount));
        return beneficiary;
    }

    @Transactional
    public Beneficiary amend(AuthPrincipal actor, UUID beneficiaryId, String name, String accountNumber,
                             String bankCode, String currency) {
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_admin manages beneficiaries");
        }
        Beneficiary beneficiary = repository.findById(beneficiaryId)
                .filter(b -> b.getCompany().getId().equals(actor.companyId()))
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found"));

        boolean identityChanged = false;
        Map<String, Object> before = Map.of(
                "name", beneficiary.getName(), "account", beneficiary.getAccountNumber(),
                "bankCode", beneficiary.getBankCode(), "status", beneficiary.getStatus().name());
        Map<String, Object> after = new java.util.LinkedHashMap<>();

        if (name != null && !name.isBlank() && !name.equals(beneficiary.getName())) {
            beneficiary.setName(cleanName(name));
            after.put("name", beneficiary.getName());
        }
        if (accountNumber != null && !accountNumber.isBlank()
                && !accountNumber.equals(beneficiary.getAccountNumber())) {
            String cleaned = cleanAccountNumber(accountNumber);
            if (repository.findByCompanyIdAndAccountNumberAndBankCode(
                    beneficiary.getCompany().getId(), cleaned, beneficiary.getBankCode()).isPresent()) {
                throw new ApiException(ApiException.Code.DUPLICATE, "A beneficiary with this account already exists");
            }
            beneficiary.setAccountNumber(cleaned);
            identityChanged = true;
            after.put("account", cleaned);
        }
        if (bankCode != null && !bankCode.isBlank() && !bankCode.equals(beneficiary.getBankCode())) {
            beneficiary.setBankCode(cleanBankCode(bankCode));
            identityChanged = true;
            after.put("bankCode", beneficiary.getBankCode());
        }
        if (currency != null && !currency.isBlank() && !currency.equalsIgnoreCase(beneficiary.getCurrency())) {
            beneficiary.setCurrency(cleanCurrency(currency));
            after.put("currency", beneficiary.getCurrency());
        }
        if (after.isEmpty()) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "No changes supplied");
        }
        beneficiary.setLastAmendedAt(Instant.now());
        beneficiary.setLastAmendedBy(userRepository.findById(actor.userId()).orElse(null));

        // Amending the account number restarts the cooling period.
        if (identityChanged) {
            BeneficiaryStateMachine.require(beneficiary.getStatus(), BeneficiaryStatus.PENDING_COOLING);
            beneficiary.setStatus(BeneficiaryStatus.PENDING_COOLING);
            beneficiary.setCoolingUntil(Instant.now().plus(COOLING_MINUTES, ChronoUnit.MINUTES));
            after.put("status", "pending_cooling");
            after.put("coolingUntil", beneficiary.getCoolingUntil().toString());
        }
        repository.save(beneficiary);
        auditService.audit(actor, "BENEFICIARY_AMENDED", "beneficiary", beneficiary.getId(), null,
                identityChanged ? "Beneficiary amended; cooling restarted" : "Beneficiary amended",
                before, after);
        return beneficiary;
    }

    @Transactional
    public Beneficiary suspend(AuthPrincipal actor, UUID beneficiaryId) {
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_admin manages beneficiaries");
        }
        Beneficiary beneficiary = repository.findById(beneficiaryId)
                .filter(b -> b.getCompany().getId().equals(actor.companyId()))
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found"));
        BeneficiaryStateMachine.require(beneficiary.getStatus(), BeneficiaryStatus.SUSPENDED);
        beneficiary.setStatus(BeneficiaryStatus.SUSPENDED);
        repository.save(beneficiary);
        auditService.audit(actor, "BENEFICIARY_SUSPENDED", "beneficiary", beneficiary.getId(), null,
                "Beneficiary suspended", Map.of("status", "active"), Map.of("status", "suspended"));
        return beneficiary;
    }

    @Transactional
    public Beneficiary reactivate(AuthPrincipal actor, UUID beneficiaryId) {
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_admin manages beneficiaries");
        }
        Beneficiary beneficiary = repository.findById(beneficiaryId)
                .filter(b -> b.getCompany().getId().equals(actor.companyId()))
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Beneficiary not found"));
        BeneficiaryStateMachine.require(beneficiary.getStatus(), BeneficiaryStatus.ACTIVE);
        beneficiary.setStatus(BeneficiaryStatus.ACTIVE);
        beneficiary.setCoolingUntil(null);
        repository.save(beneficiary);
        auditService.audit(actor, "BENEFICIARY_REACTIVATED", "beneficiary", beneficiary.getId(), null,
                "Beneficiary reactivated", Map.of("status", "suspended"), Map.of("status", "active"));
        return beneficiary;
    }

    /**
     * Lazily advances PENDING_COOLING→ACTIVE once the cooling period has elapsed. Runs whenever a
     * beneficiary is read so stale cooling states never block legitimate payments.
     */
    @Transactional
    public Beneficiary applyCoolingElapse(Beneficiary beneficiary) {
        if (beneficiary.getStatus() == BeneficiaryStatus.PENDING_COOLING
                && beneficiary.getCoolingUntil() != null
                && !beneficiary.getCoolingUntil().isAfter(Instant.now())) {
            beneficiary.setStatus(BeneficiaryStatus.ACTIVE);
            repository.save(beneficiary);
            auditService.auditSystem("cooling-scheduler", "BENEFICIARY_COOLING_ELAPSED", "beneficiary",
                    beneficiary.getId(), null,
                    "Cooling period elapsed; beneficiary is now payable",
                    Map.of("status", "pending_cooling"), Map.of("status", "active"),
                    "system=cooling-scheduler basis=cooling_elapsed");
        }
        return beneficiary;
    }

    @Transactional
    public int applyCoolingElapseForCompany(UUID companyId) {
        int count = 0;
        List<Beneficiary> pending = repository.findByStatusAndCoolingUntilBefore(
                BeneficiaryStatus.PENDING_COOLING, Instant.now());
        for (Beneficiary b : pending) {
            if (b.getCompany().getId().equals(companyId)) {
                applyCoolingElapse(b);
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ validation

    private String cleanName(String name) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Beneficiary name is required (max 200)");
        }
        return name.strip();
    }

    private String cleanAccountNumber(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("[A-Z0-9]{6,34}")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Account number must be 6-34 alphanumeric characters");
        }
        return accountNumber.toUpperCase();
    }

    private String cleanBankCode(String bankCode) {
        if (bankCode == null || !bankCode.matches("[A-Z0-9\\-]{4,34}")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED,
                    "Bank code must be 4-34 alphanumeric characters");
        }
        return bankCode.toUpperCase();
    }

    private String cleanCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Currency must be a 3-letter code");
        }
        return currency.toUpperCase();
    }
}
