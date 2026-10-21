package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.Beneficiary;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.BeneficiaryService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.CompanyDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService service;
    private final ViewMapper mapper;
    private final com.northbridge.paymentapi.service.AccessControlService access;

    public BeneficiaryController(BeneficiaryService service, ViewMapper mapper,
                                 com.northbridge.paymentapi.service.AccessControlService access) {
        this.service = service;
        this.mapper = mapper;
        this.access = access;
    }

    /**
     * Beneficiary list with an MD5-based ETag for the reference-data response (precision location #3 —
     * the MD5 use is non-cryptographic caching only).
     */
    @GetMapping
    public ResponseEntity<List<CompanyDtos.BeneficiaryView>> list(
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        AuthPrincipal actor = AuthSupport.require();
        String etag = service.etagForCompany(actor);
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        List<Beneficiary> beneficiaries = service.listForCompany(actor).stream()
                .map(b -> service.applyCoolingElapse(b))
                .toList();
        return ResponseEntity.ok().eTag(etag)
                .body(beneficiaries.stream().map(mapper::beneficiary).toList());
    }

    @PostMapping
    public CompanyDtos.BeneficiaryView create(@RequestBody Requests.CreateBeneficiaryRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Beneficiary beneficiary = service.create(actor, request.name(), request.accountNumber(),
                request.bankCode(), request.currency());
        return mapper.beneficiary(beneficiary);
    }

    @GetMapping("/{id}")
    public CompanyDtos.BeneficiaryView get(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        // Scope check via the shared access service; foreign ids yield 404.
        Beneficiary beneficiary = access.requireBeneficiaryAccess(actor, id);
        return mapper.beneficiary(service.applyCoolingElapse(beneficiary));
    }

    @PutMapping("/{id}")
    public CompanyDtos.BeneficiaryView amend(@PathVariable UUID id,
                                             @RequestBody Requests.UpdateBeneficiaryRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Beneficiary beneficiary = service.amend(actor, id, request.name(), request.accountNumber(),
                request.bankCode(), request.currency());
        return mapper.beneficiary(beneficiary);
    }

    @PostMapping("/{id}/suspend")
    public CompanyDtos.BeneficiaryView suspend(@PathVariable UUID id) {
        return mapper.beneficiary(service.suspend(AuthSupport.require(), id));
    }

    @PostMapping("/{id}/activate")
    public CompanyDtos.BeneficiaryView activate(@PathVariable UUID id) {
        return mapper.beneficiary(service.reactivate(AuthSupport.require(), id));
    }
}
