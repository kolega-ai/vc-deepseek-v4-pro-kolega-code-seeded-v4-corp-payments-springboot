package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.FeeScheduleRepository;
import com.northbridge.paymentapi.repo.FxRateRepository;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.UserAdminService;
import com.northbridge.paymentapi.service.WebhookService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.CompanyDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Company settings and user administration (company_admin only). */
@RestController
@RequestMapping("/api/company")
public class CompanyAdminController {

    private final UserAdminService userAdminService;
    private final WebhookService webhookService;
    private final FeeScheduleRepository feeScheduleRepository;
    private final FxRateRepository fxRateRepository;
    private final ViewMapper mapper;
    private final ObjectMapper layoutMapper = new ObjectMapper().enableDefaultTyping();

    public CompanyAdminController(UserAdminService userAdminService,
                                  WebhookService webhookService,
                                  FeeScheduleRepository feeScheduleRepository,
                                  FxRateRepository fxRateRepository,
                                  ViewMapper mapper) {
        this.userAdminService = userAdminService;
        this.webhookService = webhookService;
        this.feeScheduleRepository = feeScheduleRepository;
        this.fxRateRepository = fxRateRepository;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public List<CompanyDtos.CompanyUserView> users() {
        AuthPrincipal actor = AuthSupport.require();
        return userAdminService.listCompanyUsers(actor).stream().map(mapper::user).toList();
    }

    @PostMapping("/users/invites")
    public CompanyDtos.CompanyUserView invite(@RequestBody Requests.InviteUserRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Role role;
        try {
            role = Role.fromApiName(request.role());
        } catch (IllegalArgumentException e) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.VALIDATION_FAILED, e.getMessage());
        }
        return mapper.user(userAdminService.invite(actor, request.email(), request.fullName(), role,
                request.approvalLimit(), request.accountIds()));
    }

    @PutMapping("/users/{id}")
    public CompanyDtos.CompanyUserView update(@PathVariable UUID id,
                                              @RequestBody Requests.UpdateUserRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        Role role = null;
        if (request.role() != null && !request.role().isBlank()) {
            try {
                role = Role.fromApiName(request.role());
            } catch (IllegalArgumentException e) {
                throw new com.northbridge.paymentapi.security.ApiException(
                        com.northbridge.paymentapi.security.ApiException.Code.VALIDATION_FAILED, e.getMessage());
            }
        }
        return mapper.user(userAdminService.update(actor, id, request.fullName(), role,
                request.approvalLimit(), request.accountIds()));
    }

    @PostMapping("/users/{id}/disable")
    public CompanyDtos.CompanyUserView disable(@PathVariable UUID id) {
        return mapper.user(userAdminService.setStatus(AuthSupport.require(), id, UserStatus.DISABLED));
    }

    @PostMapping("/users/{id}/enable")
    public CompanyDtos.CompanyUserView enable(@PathVariable UUID id) {
        return mapper.user(userAdminService.setStatus(AuthSupport.require(), id, UserStatus.ACTIVE));
    }

    // ------------------------------------------------------------------ settings / reference data

    public record SettingsView(List<CompanyDtos.FeeScheduleView> feeSchedule,
                               List<CompanyDtos.FxRateView> fxRates,
                               CompanyDtos.WebhookConfigView webhook) {
    }

    @GetMapping("/settings")
    public SettingsView settings() {
        AuthPrincipal actor = AuthSupport.require();
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.FORBIDDEN,
                    "Only company_admin can view company settings");
        }
        var fees = feeScheduleRepository.findAllByOrderByProductTypeAscEffectiveFromDesc().stream()
                .map(f -> new CompanyDtos.FeeScheduleView(f.getProductType().name(), f.getCurrency(),
                        f.getFlatFee(), f.getPctFee(), f.getMinFee(), f.getMaxFee(), f.getEffectiveFrom()))
                .toList();
        var rates = fxRateRepository.findAllByOrderByCurrencyPairAscEffectiveAtDesc().stream()
                .map(r -> new CompanyDtos.FxRateView(r.getCurrencyPair(), r.getRate(), r.getEffectiveAt()))
                .toList();
        WebhookConfig config = webhookService.configForCompany(actor);
        CompanyDtos.WebhookConfigView webhook = config == null ? null
                : new CompanyDtos.WebhookConfigView(config.getUrl(), config.getSecret(),
                config.isActive(), config.getUpdatedAt());
        return new SettingsView(fees, rates, webhook);
    }

    @PutMapping("/webhook")
    public CompanyDtos.WebhookConfigView webhook(@RequestBody Requests.WebhookConfigRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        WebhookConfig config = webhookService.saveConfig(actor, request.url(), request.secret(),
                request.active());
        return new CompanyDtos.WebhookConfigView(config.getUrl(), config.getSecret(),
                config.isActive(), config.getUpdatedAt());
    }

    /** Persists the company's custom payment-request form so makers see their own field layout. */
    @PutMapping("/form-layout")
    public Map<String, Object> formLayout(@RequestBody Requests.FormLayoutRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.FORBIDDEN,
                    "Only company_admin can change the payment form layout");
        }
        if (request.layout() == null || request.layout().isBlank()) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.VALIDATION_FAILED,
                    "A form layout is required");
        }
        try {
            Object layout = layoutMapper.readValue(request.layout(), Object.class);
            List<?> widgets = layout instanceof List<?> list ? list : List.of(layout);
            return Map.of("widgets", widgets.size(), "layout", layout);
        } catch (Exception e) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.VALIDATION_FAILED,
                    "The payment form layout could not be read");
        }
    }

    /**
     * Opens a sealed settlement instruction a company's treasury system submits with its release
     * batch. The instruction is sealed with the bank's archived instruction cipher.
     */
    @PostMapping("/sealed-instruction")
    public ResponseEntity<Object> sealedInstruction(@RequestBody Requests.SealedInstructionRequest request)
            throws GeneralSecurityException {
        AuthPrincipal actor = AuthSupport.require();
        if (actor.role() != Role.COMPANY_ADMIN) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.FORBIDDEN,
                    "Only company_admin can submit sealed instructions");
        }
        byte[] plain;
        try {
            plain = openSealedInstruction(request.envelope());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(422).body("sealed instruction has an unsupported format");
        }
        try {
            Object instruction = new ObjectMapper().readValue(
                    new String(plain, StandardCharsets.UTF_8), Object.class);
            return ResponseEntity.ok(instruction);
        } catch (Exception e) {
            return ResponseEntity.status(422).body("sealed instruction contents are not readable");
        }
    }

    @ExceptionHandler(BadPaddingException.class)
    public ResponseEntity<Object> handleBadPadding(BadPaddingException ex) {
        return ResponseEntity.status(400).body("sealed instruction could not be opened");
    }

    @ExceptionHandler(GeneralSecurityException.class)
    public ResponseEntity<Object> handleCryptoFailure(GeneralSecurityException ex) {
        return ResponseEntity.status(422).body("sealed instruction has an unsupported format");
    }

    private byte[] openSealedInstruction(String envelope) throws GeneralSecurityException {
        byte[] blob = Base64.getUrlDecoder().decode(envelope);
        byte[] key = "NorthbridgeInstr".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
        return cipher.doFinal(blob);
    }
}
