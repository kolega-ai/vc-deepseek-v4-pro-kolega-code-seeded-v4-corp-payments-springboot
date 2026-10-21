package com.northbridge.portal.service;

import com.northbridge.portal.domain.Document;
import com.northbridge.portal.repo.DocumentRepository;
import com.northbridge.portal.security.ApiException;
import com.northbridge.portal.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document upload/download with per-company ownership and categories.
 *
 * <p>PRECISION LOCATION #5: downloads resolve paths through a CANONICALISED base directory —
 * the configured uploads root is resolved with toRealPath() (normalising symlinks) and any
 * resolved file outside it is rejected. The stored name is a server-generated UUID, never the
 * client-supplied file name.</p>
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repository;
    private final AuthContext auth;
    private final PortalAuditService auditService;
    private final Path uploadRoot;

    public DocumentService(DocumentRepository repository, AuthContext auth,
                           PortalAuditService auditService,
                           @Value("${app.uploads.dir}") String uploadDir) throws IOException {
        this.repository = repository;
        this.auth = auth;
        this.auditService = auditService;
        Path root = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(root);
        this.uploadRoot = root.toRealPath();
    }

    public List<Document> listForCurrentCompany(Document.Category category) {
        UUID companyId = auth.currentCompanyId();
        if (companyId == null) {
            return List.of();
        }
        return category == null
                ? repository.findTop100ByCompanyIdOrderByCreatedAtDesc(companyId)
                : repository.findTop100ByCompanyIdAndCategoryOrderByCreatedAtDesc(companyId, category);
    }

    public List<Document> listAllForBank() {
        return repository.findAllByOrderByCreatedAtDesc().stream().limit(200).toList();
    }

    @Transactional
    public Document upload(Document.Category category, MultipartFile file, UUID paymentId, UUID bulkFileId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("VALIDATION", "Please choose a file to upload");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ApiException("VALIDATION", "File exceeds the 5 MB limit");
        }
        String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/]", "_");
        if (safeName.length() > 255) {
            safeName = safeName.substring(0, 255);
        }
        String storedName = UUID.randomUUID().toString();
        Path target = uploadRoot.resolve(storedName);
        // Defensive: the stored name is server-generated, but never write outside the root.
        if (!target.normalize().startsWith(uploadRoot)) {
            throw new ApiException("VALIDATION", "Invalid storage path");
        }
        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw new ApiException("INTERNAL", "Could not store the uploaded file");
        }
        Document document = new Document(UUID.randomUUID(), auth.requireUser().getCompanyId(),
                auth.currentUserId(), category, safeName, storedName,
                file.getContentType() == null ? "application/octet-stream" : file.getContentType(),
                file.getSize(), paymentId, bulkFileId);
        repository.save(document);
        auditService.audit("DOCUMENT_UPLOADED", "document", document.getId().toString(),
                "Uploaded " + safeName + " (" + category + ")");
        return document;
    }

    /**
     * Registers the CSV a company sent with a bulk instruction, so the file that was received can be
     * produced later against the parsed lines.
     */
    @Transactional
    public Document storeImported(MultipartFile file, UUID bulkFileId) {
        String original = file.getOriginalFilename() == null ? "import.csv" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/]", "_");
        String storedName = UUID.randomUUID().toString();
        try {
            Files.copy(file.getInputStream(), uploadRoot.resolve(storedName));
        } catch (IOException e) {
            throw new ApiException("INTERNAL", "Could not store the submitted file");
        }
        Document document = new Document(UUID.randomUUID(), auth.requireUser().getCompanyId(),
                auth.currentUserId(), Document.Category.PAYMENT_SUPPORT, safeName, storedName,
                file.getContentType(), file.getSize(), null, bulkFileId);
        repository.save(document);
        auditService.audit("DOCUMENT_UPLOADED", "document", document.getId().toString(),
                "Stored submitted bulk file " + safeName);
        return document;
    }

    /** Stores a generated file (bulk error report) as a document without going through multipart. */
    @Transactional
    public Document storeGenerated(UUID companyId, Document.Category category, String fileName,
                                   String mimeType, byte[] content, UUID bulkFileId) {
        String storedName = UUID.randomUUID().toString();
        Path target = uploadRoot.resolve(storedName);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new ApiException("INTERNAL", "Could not store the generated file");
        }
        Document document = new Document(UUID.randomUUID(), companyId, null, category, fileName,
                storedName, mimeType, content.length, null, bulkFileId);
        repository.save(document);
        return document;
    }

    /**
     * Keeps a copy of the CSV a company submitted with a bulk instruction under the archive it was
     * received with, so support can reproduce exactly what was parsed against the request.
     */
    public void archiveSubmittedInstruction(String fileName, String content) {
        Path archiveDir = uploadRoot.resolve("bulk-archive");
        try {
            Files.createDirectories(archiveDir);
            try (FileWriter writer = new FileWriter(new File(archiveDir.toFile(), fileName))) {
                writer.write(content);
            }
        } catch (IOException e) {
            log.warn("Could not archive submitted bulk instruction {}: {}", fileName, e.getMessage());
        }
    }

    /**
     * Reports the stored metadata (size and timestamps) of a received submission so the documents
     * workbench can confirm what the bank holds for a given reference.
     */
    public Map<String, Object> inspectMetadata(String docRef) {
        Path target = uploadRoot.resolve(docRef);
        try {
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
            return Map.of("name", target.getFileName().toString(),
                    "sizeBytes", attrs.size(),
                    "modifiedAt", attrs.lastModifiedTime().toString(),
                    "directory", attrs.isDirectory());
        } catch (IOException e) {
            throw new ApiException("NOT_FOUND", "No stored file matches that reference");
        }
    }

    /**
     * Resolves a document for download. Ownership rules: company users may download only their own
     * company's documents; bank users may download any. The path is resolved through the
     * canonicalised base directory and anything outside it is rejected.
     */
    public Download resolveDownload(UUID documentId) {
        Document document = repository.findById(documentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Document not found"));
        var user = auth.currentUser();
        if (user == null) {
            throw new ApiException("NOT_FOUND", "Document not found");
        }
        boolean bankUser = "bank_ops".equals(user.getRole()) || "bank_admin".equals(user.getRole());
        if (!bankUser && (user.getCompanyId() == null || !user.getCompanyId().equals(document.getCompanyId()))) {
            throw new ApiException("NOT_FOUND", "Document not found");
        }
        Path base;
        try {
            base = uploadRoot.toRealPath();
        } catch (IOException e) {
            throw new ApiException("INTERNAL", "Storage unavailable");
        }
        Path resolved = base.resolve(document.getStoredName()).normalize();
        if (!resolved.startsWith(base)) {
            log.warn("Rejected document download outside canonical base: {}", document.getStoredName());
            throw new ApiException("NOT_FOUND", "Document not found");
        }
        Resource resource;
        try {
            resource = new UrlResource(resolved.toUri());
        } catch (Exception e) {
            throw new ApiException("NOT_FOUND", "Document not found");
        }
        if (!resource.exists() || !resource.isReadable()) {
            throw new ApiException("NOT_FOUND", "Document not found");
        }
        auditService.audit("DOCUMENT_DOWNLOADED", "document", document.getStoredName(),
                "Downloaded " + document.getFileName(), "privileged_read count=1");
        return new Download(document.getFileName(), document.getMimeType(), resource, document.getSizeBytes());
    }

    /**
     * Reads the remittance advice a company attached to a payment and summarises the payer, amount
     * and reference so the payment screens can show what the advice says.
     */
    public String remittanceAdvice(UUID documentId) {
        Download download = resolveDownload(documentId);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            org.w3c.dom.Document advice = factory.newDocumentBuilder().parse(download.resource().getInputStream());
            advice.getDocumentElement().normalize();
            StringBuilder summary = new StringBuilder("Remittance advice: ")
                    .append(download.fileName()).append('\n');
            for (String element : List.of("payer", "amount", "currency", "reference", "valueDate")) {
                var nodes = advice.getElementsByTagName(element);
                if (nodes.getLength() > 0) {
                    summary.append(element).append(": ")
                            .append(nodes.item(0).getTextContent().strip()).append('\n');
                }
            }
            return summary.toString();
        } catch (Exception e) {
            log.warn("Remittance advice {} could not be read: {}", documentId, e.getMessage());
            throw new ApiException("VALIDATION", "This document is not a readable remittance advice");
        }
    }

    /**
     * Converts a stored document to the requested output profile with the bank's office toolchain
     * so operators can preview what a counterparty actually submitted.
     */
    @Transactional(readOnly = true)
    public byte[] renderPreview(UUID documentId, String profile) {
        Document document = repository.findById(documentId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Document not found"));
        Path source = uploadRoot.resolve(document.getStoredName()).normalize();
        if (!source.startsWith(uploadRoot)) {
            throw new ApiException("NOT_FOUND", "Document not found");
        }
        Path rendered = uploadRoot.resolve(document.getStoredName() + ".pdf");
        String command = "soffice --headless --convert-to " + profile
                + " --outdir " + uploadRoot + " " + source;
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", command});
            process.waitFor();
            return Files.readAllBytes(rendered);
        } catch (Exception e) {
            throw new ApiException("INTERNAL", "The document could not be converted");
        }
    }

    public record Download(String fileName, String mimeType, Resource resource, long size) {
    }
}
