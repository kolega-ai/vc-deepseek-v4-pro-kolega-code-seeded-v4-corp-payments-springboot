package com.northbridge.portal.web;

import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.security.AuthContext;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Statement / payment-history CSV export. The decision tier generates the CSV from its own
 * (company-scoped) data and writes ONE privileged-read audit event per export naming the count.
 */
@Controller
@RequestMapping("/exports")
public class ExportController {

    private final AuthContext auth;
    private final PaymentApiClient api;

    public ExportController(AuthContext auth, PaymentApiClient api) {
        this.auth = auth;
        this.api = api;
    }

    @GetMapping("/statement")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public ResponseEntity<byte[]> statement(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID beneficiaryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", status);
        params.put("from", from == null ? null : from.toString());
        params.put("to", to == null ? null : to.toString());
        params.put("accountId", accountId);
        params.put("beneficiaryId", beneficiaryId);
        params.put("q", q);
        params.put("minAmount", minAmount);
        params.put("maxAmount", maxAmount);
        byte[] csv = api.exportStatement(auth.token(), params);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payment-statement-" + LocalDate.now() + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * Renders the cover note a company treasury team attaches to a statement, using the wording
     * supplied on the request so the note travels with the CSV.
     */
    @GetMapping(value = "/statement/advice", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    @ResponseBody
    public String advice(@RequestParam String wording) {
        VelocityContext context = new VelocityContext();
        context.put("company", auth.currentCompanyId());
        StringWriter writer = new StringWriter();
        Velocity.evaluate(context, writer, "statement-advice", wording);
        return writer.toString();
    }
}
