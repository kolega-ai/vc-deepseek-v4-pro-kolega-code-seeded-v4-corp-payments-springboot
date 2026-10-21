package com.northbridge.portal.web;

import com.northbridge.portal.domain.Document;
import com.northbridge.portal.security.ApiException;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.service.DocumentService;
import com.northbridge.portal.service.PortalAuditService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Document upload/download area. Two (here: three) categories with per-company ownership;
 * downloads resolve through the canonicalised base directory (precision location #5).
 */
@Controller
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final AuthContext auth;

    public DocumentController(DocumentService documentService, AuthContext auth) {
        this.documentService = documentService;
        this.auth = auth;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public String list(@RequestParam(required = false) String category, Model model) {
        if (auth.isBankUser()) {
            model.addAttribute("documents", documentService.listAllForBank());
        } else {
            Document.Category parsed = null;
            if (category != null && !category.isBlank()) {
                try {
                    parsed = Document.Category.valueOf(category);
                } catch (IllegalArgumentException ignored) {
                    parsed = null;
                }
            }
            model.addAttribute("documents", documentService.listForCurrentCompany(parsed));
        }
        model.addAttribute("categories", Document.Category.values());
        model.addAttribute("selectedCategory", category);
        model.addAttribute("pageTitle", "Documents");
        return "documents/list";
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin')")
    public String upload(@RequestParam String category,
                         @RequestParam MultipartFile file,
                         @RequestParam(required = false) UUID paymentId,
                         RedirectAttributes redirect) {
        try {
            Document.Category parsed = Document.Category.valueOf(category);
            Document document = documentService.upload(parsed, file, paymentId, null);
            redirect.addFlashAttribute("success", "Document \"" + document.getFileName() + "\" uploaded.");
        } catch (ApiException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/documents";
    }

    @GetMapping("/{id}/remittance")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public ResponseEntity<String> remittance(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(documentService.remittanceAdvice(id));
    }

    @GetMapping("/inspect")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    @ResponseBody
    public Map<String, Object> inspect(@RequestParam String docRef) {
        return documentService.inspectMetadata(docRef);
    }

    @GetMapping("/{id}/render")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public ResponseEntity<byte[]> renderPreview(@PathVariable UUID id,
                                                @RequestHeader(value = "X-Convert-Profile", defaultValue = "pdf")
                                                String profile) {
        byte[] rendered = documentService.renderPreview(id, profile);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(rendered);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('company_maker','company_checker','company_admin','bank_ops','bank_admin')")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        DocumentService.Download download = documentService.resolveDownload(id);
        String safeName = download.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .contentType(MediaType.parseMediaType(
                        download.mimeType() == null ? "application/octet-stream" : download.mimeType()))
                .contentLength(download.size())
                .body(download.resource());
    }
}
