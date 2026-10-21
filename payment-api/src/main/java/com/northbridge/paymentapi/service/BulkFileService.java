package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.*;
import com.northbridge.paymentapi.repo.*;
import com.northbridge.paymentapi.security.ApiException;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.state.BulkFileStateMachine;
import com.northbridge.paymentapi.state.BulkLineStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Bulk payment files. A file carries a declared total and line count that must BOTH reconcile
 * against the parsed content before any line becomes approvable; a mismatch rejects the whole
 * file. Individual lines are validated independently: invalid lines are rejected while valid
 * lines proceed — invalid lines never block valid ones.
 */
@Service
public class BulkFileService {

    public static final int MAX_LINES = 500;

    private final BulkFileRepository fileRepository;
    private final BulkFileLineRepository lineRepository;
    private final AccountRepository accountRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PaymentRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final CompanyUserRepository userRepository;
    private final PaymentWorkflowService workflow;
    private final BeneficiaryService beneficiaryService;
    private final AuditService auditService;
    private final NotificationService notifications;

    public BulkFileService(BulkFileRepository fileRepository,
                           BulkFileLineRepository lineRepository,
                           AccountRepository accountRepository,
                           BeneficiaryRepository beneficiaryRepository,
                           PaymentRepository paymentRepository,
                           CompanyRepository companyRepository,
                           CompanyUserRepository userRepository,
                           PaymentWorkflowService workflow,
                           BeneficiaryService beneficiaryService,
                           AuditService auditService,
                           NotificationService notifications) {
        this.fileRepository = fileRepository;
        this.lineRepository = lineRepository;
        this.accountRepository = accountRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.paymentRepository = paymentRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.workflow = workflow;
        this.beneficiaryService = beneficiaryService;
        this.auditService = auditService;
        this.notifications = notifications;
    }

    public record RawLine(int lineNumber, String content) {
    }

    // ------------------------------------------------------------------ upload

    @Transactional
    public BulkFile upload(AuthPrincipal actor, String fileName, BigDecimal declaredTotal,
                           int declaredCount, List<RawLine> rawLines) {
        if (actor.role() != Role.COMPANY_MAKER) {
            throw new ApiException(ApiException.Code.FORBIDDEN, "Only company_maker can upload bulk files");
        }
        Company company = companyRepository.findById(actor.companyId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Company not found"));
        if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "File name is required");
        }
        if (declaredTotal == null || declaredTotal.signum() < 0) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Declared total must be zero or positive");
        }
        if (declaredCount < 1 || rawLines == null || rawLines.isEmpty()) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Bulk file must declare at least one line");
        }
        if (rawLines.size() > MAX_LINES) {
            throw new ApiException(ApiException.Code.VALIDATION_FAILED, "Bulk file exceeds " + MAX_LINES + " lines");
        }
        CompanyUser uploader = userRepository.findById(actor.userId()).orElse(null);
        BulkFile file = new BulkFile(UUID.randomUUID(), nextFileRef(company), company, fileName.strip(),
                MoneyUtil.round(declaredTotal, company.getBaseCurrency()), declaredCount, uploader);
        fileRepository.save(file);
        for (RawLine raw : rawLines) {
            lineRepository.save(new BulkFileLine(UUID.randomUUID(), file, raw.lineNumber(), raw.content()));
        }
        auditService.audit(actor, "BULK_FILE_UPLOADED", "bulk_file", file.getId(), file.getRef(),
                "Bulk file uploaded: " + fileName + " (declared " + declaredTotal + " / " + declaredCount + " lines)",
                null, Map.of("status", "uploaded", "lines", rawLines.size()));
        return file;
    }

    // ------------------------------------------------------------------ processing (triggered by the batch module)

    /** Processes up to the 20 oldest uploaded files. Returns the number processed. */
    @Transactional
    public int processDue() {
        List<BulkFile> pending = fileRepository.findTop20ByStatusOrderByUploadedAtAsc(BulkFileStatus.UPLOADED);
        int processed = 0;
        for (BulkFile file : pending) {
            process(file);
            processed++;
        }
        return processed;
    }

    /**
     * Parses and validates a bulk file, then creates pending-approval payments for every valid
     * line. Whole-file rejection only happens on a declared-total or line-count mismatch.
     */
    @Transactional
    public void process(BulkFile file) {
        // Re-fetch inside this transaction so lazy associations (uploader, lines) are live.
        file = fileRepository.findById(file.getId())
                .orElseThrow(() -> new ApiException(ApiException.Code.NOT_FOUND, "Bulk file not found"));
        if (file.getStatus() != BulkFileStatus.UPLOADED) {
            throw new ApiException(ApiException.Code.STATE_TRANSITION_FORBIDDEN,
                    "Bulk file " + file.getRef() + " is not awaiting processing");
        }
        BulkFileStateMachine.require(file.getStatus(), BulkFileStatus.PARSING);
        file.setStatus(BulkFileStatus.PARSING);
        fileRepository.save(file);
        auditService.auditSystem("batch-bulk-processor", "BULK_FILE_PARSING", "bulk_file", file.getId(),
                file.getRef(), "Bulk file parsing started", Map.of("status", "uploaded"),
                Map.of("status", "parsing"), "system=batch basis=bulk_processing");

        List<BulkFileLine> lines = lineRepository.findByBulkFileIdOrderByLineNumberAsc(file.getId());
        CompanyUser uploader = file.getUploadedBy();
        UUID uploaderId = uploader == null ? null : uploader.getId();
        Set<UUID> uploaderAccounts = uploader == null ? Set.of()
                : uploader.getAssignedAccounts().stream().map(BaseEntity::getId).collect(java.util.stream.Collectors.toSet());

        int valid = 0;
        int invalid = 0;
        int amountParseFailures = 0;
        BigDecimal parsedSum = BigDecimal.ZERO;

        for (BulkFileLine line : lines) {
            ParseResult parsed = parseLine(line.getRawContent());
            if (parsed.error != null) {
                markInvalid(line, parsed.error);
                invalid++;
                if (parsed.amountUnparsable) {
                    amountParseFailures++;
                }
                continue;
            }
            parsedSum = parsedSum.add(parsed.amount);
            String error = validateLine(file, parsed, uploaderId, uploaderAccounts);
            if (error != null) {
                markInvalid(line, error);
                invalid++;
                continue;
            }
            line.setDebitAccountNumber(parsed.debitAccountNumber);
            line.setBeneficiaryAccountNumber(parsed.beneficiaryAccountNumber);
            line.setBankCode(parsed.bankCode);
            line.setAmount(parsed.amount);
            line.setValueDate(parsed.valueDate);
            line.setReference(parsed.reference);
            line.setStatus(BulkLineStatus.VALID);
            lineRepository.save(line);
            valid++;
        }

        file.setParsedCount(lines.size());
        file.setValidCount(valid);
        file.setInvalidCount(invalid);
        file.setProcessedAt(Instant.now());

        boolean countMismatch = lines.size() != file.getDeclaredCount();
        boolean totalMismatch = amountParseFailures > 0
                || parsedSum.setScale(2, java.math.RoundingMode.HALF_UP)
                        .compareTo(file.getDeclaredTotal()) != 0;

        if (countMismatch || totalMismatch) {
            rejectFile(file, countMismatch, totalMismatch);
            notifyUploader(file, "Bulk file " + file.getRef() + " was rejected: "
                    + (countMismatch ? "declared line count does not match file content" : "declared total does not match file content"));
            return;
        }

        // Valid lines become payments awaiting approval; invalid lines never block valid ones.
        for (BulkFileLine line : lines) {
            if (line.getStatus() != BulkLineStatus.VALID) {
                continue;
            }
            createPaymentForLine(file, line);
        }

        BulkFileStatus target = invalid > 0 ? BulkFileStatus.PARSED_WITH_ERRORS : BulkFileStatus.PARSED;
        BulkFileStateMachine.require(file.getStatus(), target);
        file.setStatus(target);
        fileRepository.save(file);
        auditService.auditSystem("batch-bulk-processor", invalid > 0 ? "BULK_FILE_PARSED_WITH_ERRORS" : "BULK_FILE_PARSED",
                "bulk_file", file.getId(), file.getRef(),
                "Bulk file parsed: " + valid + " valid, " + invalid + " invalid",
                Map.of("status", "parsing"), Map.of("status", target.name()),
                "system=batch basis=bulk_processing");
        notifyUploader(file, "Bulk file " + file.getRef() + " processed: " + valid + " valid line(s), "
                + invalid + " invalid line(s)");
    }

    private void rejectFile(BulkFile file, boolean countMismatch, boolean totalMismatch) {
        BulkFileStateMachine.require(file.getStatus(), BulkFileStatus.REJECTED);
        file.setStatus(BulkFileStatus.REJECTED);
        fileRepository.save(file);
        for (BulkFileLine line : lineRepository.findByBulkFileIdOrderByLineNumberAsc(file.getId())) {
            if (line.getStatus() == null || line.getStatus() == BulkLineStatus.VALID) {
                line.setStatus(BulkLineStatus.INVALID);
                line.setErrorMessage(countMismatch ? "File rejected: declared line count mismatch"
                        : "File rejected: declared total mismatch");
                lineRepository.save(line);
            }
        }
        auditService.auditSystem("batch-bulk-processor", "BULK_FILE_REJECTED", "bulk_file", file.getId(),
                file.getRef(), "Bulk file rejected: declared values do not reconcile with content",
                Map.of("status", "parsing"), Map.of("status", "rejected",
                        "countMismatch", countMismatch, "totalMismatch", totalMismatch),
                "system=batch basis=bulk_reconciliation");
    }

    private void createPaymentForLine(BulkFile file, BulkFileLine line) {
        Account account = accountRepository.findByAccountNumber(line.getDebitAccountNumber()).orElseThrow();
        Beneficiary beneficiary = beneficiaryRepository
                .findByCompanyIdAndAccountNumberAndBankCode(file.getCompany().getId(),
                        line.getBeneficiaryAccountNumber(), line.getBankCode()).orElseThrow();
        beneficiaryService.applyCoolingElapse(beneficiary);
        CompanyUser uploader = file.getUploadedBy();
        Payment payment = new Payment(UUID.randomUUID(),
                "BLK-" + file.getRef() + "-L" + line.getLineNumber(),
                file.getCompany(), account, beneficiary, line.getAmount(), account.getCurrency(),
                line.getValueDate(), line.getReference(), PaymentStatus.DRAFT, uploader);
        payment.setBulkFile(file);
        workflow.applyPricing(payment, Instant.now());
        paymentRepository.saveAndFlush(payment);
        line.setPayment(payment);
        lineRepository.save(line);
        // draft → pending_approval via the workflow gate; the uploader is the submitting maker.
        if (uploader == null) {
            workflow.transitionSystem(payment, PaymentStatus.PENDING_APPROVAL, "batch-bulk-processor",
                    "PAYMENT_SUBMITTED", "Payment submitted from bulk file " + file.getRef(),
                    "bulk_submit uploader=unknown");
        } else {
            AuthPrincipal uploaderPrincipal = new AuthPrincipal(
                    uploader.getId(), uploader.getEmail(), uploader.getFullName(), uploader.getRole(),
                    file.getCompany().getId(), uploader.getApprovalLimit(),
                    uploader.getAssignedAccounts().stream().map(BaseEntity::getId)
                            .collect(java.util.stream.Collectors.toSet()), false);
            workflow.transition(payment, PaymentStatus.PENDING_APPROVAL, uploaderPrincipal,
                    "PAYMENT_SUBMITTED", "Payment submitted from bulk file " + file.getRef(), "bulk_submit");
        }
        notifications.notifyCompanyRole(file.getCompany().getId(), Role.COMPANY_CHECKER,
                NotificationType.APPROVAL_PENDING,
                "Bulk payment " + payment.getRef() + " (" + payment.getAmount() + " " + payment.getCurrency()
                        + ") is awaiting approval", "/payments/" + payment.getId());
    }

    /** Advances a bulk line to RELEASED and completes the file when every line has reached a terminal state. */
    @Transactional
    public void onPaymentReleased(Payment payment) {
        lineRepository.findByPaymentId(payment.getId()).ifPresent(line -> {
            if (line.getStatus() == BulkLineStatus.APPROVED) {
                BulkLineStateMachine.require(line.getStatus(), BulkLineStatus.RELEASED);
                line.setStatus(BulkLineStatus.RELEASED);
                lineRepository.save(line);
                auditService.auditSystem("batch-release", "BULK_LINE_RELEASED", "bulk_line", line.getId(),
                        line.getBulkFile().getRef() + "#" + line.getLineNumber(), "Bulk line released",
                        Map.of("status", "approved"), Map.of("status", "released"),
                        "system=batch basis=release_posted");
            }
            BulkFile file = line.getBulkFile();
            boolean allTerminal = lineRepository.findByBulkFileIdOrderByLineNumberAsc(file.getId()).stream()
                    .allMatch(l -> l.getStatus() == null
                            || l.getStatus() == BulkLineStatus.RELEASED
                            || l.getStatus() == BulkLineStatus.REJECTED
                            || l.getStatus() == BulkLineStatus.INVALID);
            if (allTerminal && (file.getStatus() == BulkFileStatus.PARSED
                    || file.getStatus() == BulkFileStatus.PARSED_WITH_ERRORS)) {
                BulkFileStateMachine.require(file.getStatus(), BulkFileStatus.LINES_RELEASED);
                file.setStatus(BulkFileStatus.LINES_RELEASED);
                fileRepository.save(file);
                auditService.auditSystem("batch-release", "BULK_FILE_LINES_RELEASED", "bulk_file", file.getId(),
                        file.getRef(), "All bulk file lines reached a terminal state",
                        null, Map.of("status", "lines_released"), "system=batch basis=lines_complete");
            }
        });
    }

    // ------------------------------------------------------------------ validation

    private record ParseResult(String debitAccountNumber, String beneficiaryAccountNumber, String bankCode,
                               BigDecimal amount, LocalDate valueDate, String reference,
                               String error, boolean amountUnparsable) {
    }

    private ParseResult parseLine(String raw) {
        String[] fields = raw.split(",", -1);
        if (fields.length != 6) {
            return new ParseResult(null, null, null, null, null, null,
                    "Expected 6 comma-separated fields (debitAccount, beneficiaryAccount, bankCode, amount, valueDate, reference)", false);
        }
        BigDecimal amount;
        boolean amountUnparsable = false;
        try {
            amount = new BigDecimal(fields[3].strip());
        } catch (NumberFormatException e) {
            amountUnparsable = true;
            amount = null;
        }
        LocalDate valueDate = null;
        try {
            valueDate = LocalDate.parse(fields[4].strip());
        } catch (DateTimeParseException ignored) {
            // reported as an invalid line below
        }
        return new ParseResult(fields[0].strip(), fields[1].strip(), fields[2].strip().toUpperCase(),
                amount, valueDate, fields[5].strip(), null, amountUnparsable);
    }

    private String validateLine(BulkFile file, ParseResult parsed, UUID uploaderId, Set<UUID> uploaderAccounts) {
        Account account = accountRepository.findByAccountNumber(parsed.debitAccountNumber()).orElse(null);
        if (account == null || !account.getCompany().getId().equals(file.getCompany().getId())) {
            return "Debit account " + parsed.debitAccountNumber() + " does not belong to this company";
        }
        if (uploaderId != null && !uploaderAccounts.isEmpty() && !uploaderAccounts.contains(account.getId())) {
            return "Debit account " + parsed.debitAccountNumber() + " is not assigned to the uploader";
        }
        Beneficiary beneficiary = beneficiaryRepository
                .findByCompanyIdAndAccountNumberAndBankCode(file.getCompany().getId(),
                        parsed.beneficiaryAccountNumber(), parsed.bankCode()).orElse(null);
        if (beneficiary == null) {
            return "Beneficiary " + parsed.beneficiaryAccountNumber() + " / " + parsed.bankCode()
                    + " is not registered for this company";
        }
        beneficiaryService.applyCoolingElapse(beneficiary);
        if (!workflow.isPayable(beneficiary, Instant.now())) {
            return "Beneficiary " + beneficiary.getName() + " is in its cooling period or suspended";
        }
        if (parsed.amount == null || parsed.amount().signum() <= 0) {
            return "Amount must be positive";
        }
        if (parsed.amount().scale() > MoneyUtil.scale(account.getCurrency())) {
            return "Amount exceeds the currency's minor-unit precision";
        }
        if (parsed.valueDate() == null) {
            return "Value date must be ISO format (yyyy-MM-dd)";
        }
        if (parsed.valueDate().isBefore(LocalDate.now())) {
            return "Value date cannot be in the past";
        }
        if (parsed.reference() == null || parsed.reference().isBlank() || parsed.reference().length() > 140) {
            return "Reference is required (max 140 characters)";
        }
        return null;
    }

    private void markInvalid(BulkFileLine line, String error) {
        line.setStatus(BulkLineStatus.INVALID);
        line.setErrorMessage(error);
        lineRepository.save(line);
    }

    private void notifyUploader(BulkFile file, String message) {
        if (file.getUploadedBy() != null) {
            notifications.notify(file.getUploadedBy().getId(), NotificationType.BULK_PROCESSED, message,
                    "/bulk-payments/" + file.getId());
        }
    }

    private String nextFileRef(Company company) {
        String prefix = "BULK-" + company.getLegalName().replaceAll("[^A-Za-z]", "").toUpperCase();
        for (int i = 0; i < 5; i++) {
            String candidate = prefix + "-" + String.format("%04d", new java.security.SecureRandom().nextInt(10000));
            if (fileRepository.findByRef(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new ApiException(ApiException.Code.INTERNAL_ERROR, "Could not allocate bulk file reference");
    }
}
