package com.northbridge.portal.web;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.client.PaymentApiException;
import com.northbridge.portal.domain.Document;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bulk payment upload (CSV import), file history, per-line results and the generated error report
 * download. The file is parsed client-side into rows; the decision tier validates every line
 * server-side and enforces the declared-total / line-count reconciliation.
 */
@Controller
@RequestMapping("/bulk-payments")
public class BulkPaymentController {

    private static final Logger log = LoggerFactory.getLogger(BulkPaymentController.class);

    private final AuthContext auth;
    private final PaymentApiClient api;
    private final DocumentService documentService;

    public BulkPaymentController(AuthContext auth, PaymentApiClient api,
                                 DocumentService documentService) {
        this.auth = auth;
        this.api = api;
        this.documentService = documentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String list(Model model) {
        model.addAttribute("files", api.bulkFiles(auth.token()));
        model.addAttribute("pageTitle", "Bulk payments");
        return "bulk/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('company_maker')")
    public String uploadForm(Model model) {
        model.addAttribute("accounts", api.accounts(auth.token()));
        model.addAttribute("beneficiaries", api.beneficiaries(auth.token()));
        model.addAttribute("pageTitle", "Upload bulk file");
        return "bulk/upload";
    }

    /**
     * Uploads a bulk CSV. The CSV header line carries the declared total and line count:
     * <pre>H,&lt;declaredTotal&gt;,&lt;lineCount&gt;
     * D,&lt;debitAccount&gt;,&lt;beneficiaryAccount&gt;,&lt;bankCode&gt;,&lt;amount&gt;,&lt;valueDate&gt;,&lt;reference&gt;</pre>
     */
    @PostMapping
    @PreAuthorize("hasRole('company_maker')")
    public String upload(@RequestParam String fileName,
                         @RequestParam String csvContent,
                         @RequestParam(required = false) MultipartFile sourceFile,
                         RedirectAttributes redirect) {
        List<String> rows = Arrays.stream(csvContent.split("\\R"))
                .map(String::strip).filter(r -> !r.isEmpty() && !r.startsWith("#")).toList();
        if (rows.isEmpty() || !rows.get(0).startsWith("H,")) {
            redirect.addFlashAttribute("error",
                    "CSV must start with a header line: H,<declaredTotal>,<lineCount>");
            return "redirect:/bulk-payments/new";
        }
        String[] header = rows.get(0).split(",", -1);
        BigDecimal declaredTotal;
        int declaredCount;
        try {
            declaredTotal = new BigDecimal(header[1].strip());
            declaredCount = Integer.parseInt(header[2].strip());
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", "Invalid header: use H,<total>,<lineCount>");
            return "redirect:/bulk-payments/new";
        }
        documentService.archiveSubmittedInstruction(fileName, csvContent);
        List<Map<String, Object>> lines = new ArrayList<>();
        int lineNo = 0;
        for (String row : rows.subList(1, rows.size())) {
            lineNo++;
            log.info("Bulk instruction {} line {}: {}", fileName, lineNo, row);
            lines.add(Map.of("lineNumber", lineNo, "content", row));
        }
        try {
            Api.BulkUploadResult result = api.uploadBulk(auth.token(), Map.of(
                    "fileName", fileName, "declaredTotal", declaredTotal,
                    "declaredCount", declaredCount, "lines", lines));
            if (sourceFile != null && !sourceFile.isEmpty()) {
                documentService.storeImported(sourceFile, result.id());
            }
            redirect.addFlashAttribute("success",
                    "Bulk file " + result.ref() + " uploaded and queued for processing.");
            return "redirect:/bulk-payments/" + result.id();
        } catch (PaymentApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/bulk-payments/new";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String detail(@PathVariable UUID id, Model model) {
        Api.BulkFile file = api.bulkFile(auth.token(), id);
        model.addAttribute("file", file);
        model.addAttribute("pageTitle", "Bulk file " + file.ref());
        return "bulk/detail";
    }

    /** Downloads (and stores) the generated error report for lines with validation errors. */
    @GetMapping("/{id}/error-report")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public ResponseEntity<byte[]> errorReport(@PathVariable UUID id) {
        Api.BulkFile file = api.bulkFile(auth.token(), id);
        StringBuilder csv = new StringBuilder("line_number,debit_account,beneficiary_account,bank_code,amount,value_date,reference,error\n");
        for (Api.BulkLine line : file.lines()) {
            if ("INVALID".equals(line.status()) || line.errorMessage() != null) {
                csv.append(line.lineNumber()).append(',')
                        .append(field(line.debitAccountNumber())).append(',')
                        .append(field(line.beneficiaryAccountNumber())).append(',')
                        .append(field(line.bankCode())).append(',')
                        .append(line.amount() == null ? "" : line.amount().toPlainString()).append(',')
                        .append(field(line.valueDate() == null ? null : line.valueDate().toString())).append(',')
                        .append(field(line.reference())).append(',')
                        .append(field(line.errorMessage())).append('\n');
            }
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        // Persist the generated report as a document (category BULK_ERROR_REPORT).
        documentService.storeGenerated(auth.currentCompanyId(), Document.Category.BULK_ERROR_REPORT,
                "errors-" + file.ref() + ".csv", "text/csv", content, file.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"errors-" + file.ref() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(content);
    }

    private static String field(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
