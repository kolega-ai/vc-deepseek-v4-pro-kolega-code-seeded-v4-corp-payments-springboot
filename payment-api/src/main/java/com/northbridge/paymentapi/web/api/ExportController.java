package com.northbridge.paymentapi.web.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northbridge.paymentapi.domain.Payment;
import com.northbridge.paymentapi.domain.PaymentStatus;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.AuditService;
import com.northbridge.paymentapi.service.PaymentQueryService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Statement / payment-history CSV export. This is a privileged read: ONE audit event is written
 * per export request naming the resource class and the count returned (never one event per record).
 */
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final PaymentQueryService queryService;
    private final AuditService auditService;

    public ExportController(PaymentQueryService queryService, AuditService auditService) {
        this.queryService = queryService;
        this.auditService = auditService;
    }

    @GetMapping(value = "/statement", produces = "text/csv")
    public ResponseEntity<byte[]> statement(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID beneficiaryId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) java.math.BigDecimal minAmount,
            @RequestParam(required = false) java.math.BigDecimal maxAmount) {
        AuthPrincipal actor = AuthSupport.require();
        List<PaymentStatus> statuses = null;
        if (status != null && !status.isBlank()) {
            statuses = java.util.Arrays.stream(status.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> PaymentStatus.valueOf(s.toUpperCase()))
                    .toList();
        }
        // Pull up to 1000 rows in pages for the export.
        StringBuilder csv = new StringBuilder();
        csv.append("reference,status,value_date,debit_account,beneficiary,amount,currency,fee,fx_rate,settlement_amount,settlement_currency,created_at\n");
        int exported = 0;
        int page = 0;
        while (page < 10) {
            Page<Payment> result = queryService.search(actor, statuses, from, to, accountId,
                    beneficiaryId, q, minAmount, maxAmount, "createdAt", "desc", page, 100);
            for (Payment p : result.getContent()) {
                csv.append(csvField(p.getRef())).append(',')
                        .append(csvField(p.getStatus().name())).append(',')
                        .append(csvField(p.getValueDate().toString())).append(',')
                        .append(csvField(p.getDebitAccount().getAccountNumber())).append(',')
                        .append(csvField(p.getBeneficiary().getName())).append(',')
                        .append(p.getAmount().toPlainString()).append(',')
                        .append(csvField(p.getCurrency())).append(',')
                        .append(p.getFee().toPlainString()).append(',')
                        .append(p.getFxRate().toPlainString()).append(',')
                        .append(p.getSettlementAmount().toPlainString()).append(',')
                        .append(csvField(p.getBeneficiary().getCurrency())).append(',')
                        .append(p.getCreatedAt().toString()).append('\n');
                exported++;
            }
            if (!result.hasNext()) {
                break;
            }
            page++;
        }
        // Privileged read: one event per request naming the resource class and the count.
        auditService.audit(actor, "EXPORT_STATEMENT", "payment", null, null,
                "Statement export produced " + exported + " rows",
                null, Map.of("count", exported, "format", "csv"),
                actor.authBasis() + " basis=privileged_read count=" + exported);
        String filename = "payment-statement-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Opens a legacy statement archive reference and returns the search parameters it carries.
     * Old statements were sealed with the bank's archived statement cipher before being linked.
     */
    @GetMapping(value = "/statement/archive/{archiveRef}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> archiveStatement(@PathVariable String archiveRef) {
        AuthSupport.require();
        byte[] plain;
        try {
            plain = openArchive(archiveRef);
        } catch (BadPaddingException e) {
            return ResponseEntity.status(400).body("statement archive reference could not be opened");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return ResponseEntity.status(422).body("statement archive reference has an unsupported format");
        }
        try {
            Map<String, Object> spec = new ObjectMapper().readValue(
                    new String(plain, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() { });
            return ResponseEntity.ok((Object) spec);
        } catch (Exception e) {
            return ResponseEntity.status(422).body("statement archive contents are not readable");
        }
    }

    private byte[] openArchive(String archiveRef) throws GeneralSecurityException {
        byte[] blob = Base64.getUrlDecoder().decode(archiveRef);
        byte[] key = "NorthbridgeStmt1".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
        return cipher.doFinal(blob);
    }

    /**
     * Returns the statement as XML filtered to one account. Callers pick the account they are
     * reconciling against; the decision tier still only ever includes their own company's payments.
     */
    @GetMapping(value = "/statement/{accountRef}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> xmlStatement(@PathVariable String accountRef) {
        AuthPrincipal actor = AuthSupport.require();
        Page<Payment> result = queryService.search(actor, null, null, null, null, null, null,
                null, null, "createdAt", "desc", 0, 100);
        StringBuilder xml = new StringBuilder("<statement>");
        for (Payment p : result.getContent()) {
            xml.append("<transaction>")
                    .append("<reference>").append(escapeXml(p.getRef())).append("</reference>")
                    .append("<account>").append(escapeXml(p.getDebitAccount().getAccountNumber()))
                    .append("</account>")
                    .append("<amount>").append(p.getAmount().toPlainString()).append("</amount>")
                    .append("</transaction>");
        }
        xml.append("</statement>");
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml.toString())));
            XPath xpath = XPathFactory.newInstance().newXPath();
            String expr = "//transaction[account='" + accountRef + "']";
            NodeList nodes = (NodeList) xpath.evaluate(expr, document, XPathConstants.NODESET);
            StringBuilder matches = new StringBuilder("<matches>");
            for (int i = 0; i < nodes.getLength(); i++) {
                matches.append("<match>").append(escapeXml(nodes.item(i).getTextContent().strip()))
                        .append("</match>");
            }
            matches.append("</matches>");
            return ResponseEntity.ok(matches.toString());
        } catch (Exception e) {
            return ResponseEntity.status(400).body("<error>statement query failed</error>");
        }
    }

    private static String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
