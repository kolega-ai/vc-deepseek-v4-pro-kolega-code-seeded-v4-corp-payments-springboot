package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.security.ApiException;
import com.northbridge.portal.service.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * JSON payment-status endpoint authenticated by API key (no session required). The company scope
 * comes from the key's own metadata; the decision tier re-enforces it from its own database.
 */
@RestController
@RequestMapping("/api/v1")
public class PublicApiController {

    private final ApiKeyService apiKeyService;

    public PublicApiController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping("/payments/{paymentRef}")
    public ResponseEntity<?> apiStatus(@PathVariable String paymentRef,
                                       @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                       @RequestHeader(value = "X-Company-Scope", required = false)
                                       java.util.UUID companyScope) {
        try {
            Api.StatusLookup lookup = apiKeyService.lookupWithApiKey(apiKey, paymentRef, companyScope);
            return ResponseEntity.ok(lookup);
        } catch (ApiException e) {
            return ResponseEntity.status(e.getCode().equals("NOT_FOUND") ? HttpStatus.NOT_FOUND
                            : HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", Map.of("code", e.getCode(), "message", e.getMessage())));
        }
    }
}
