package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.BulkFile;
import com.northbridge.paymentapi.repo.BulkFileLineRepository;
import com.northbridge.paymentapi.repo.BulkFileRepository;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.AccessControlService;
import com.northbridge.paymentapi.service.BulkFileService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.PaymentDtos;
import com.northbridge.paymentapi.web.dto.Requests;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bulk-files")
public class BulkFileController {

    private final BulkFileService service;
    private final BulkFileRepository fileRepository;
    private final BulkFileLineRepository lineRepository;
    private final AccessControlService access;
    private final ViewMapper mapper;

    public BulkFileController(BulkFileService service,
                              BulkFileRepository fileRepository,
                              BulkFileLineRepository lineRepository,
                              AccessControlService access,
                              ViewMapper mapper) {
        this.service = service;
        this.fileRepository = fileRepository;
        this.lineRepository = lineRepository;
        this.access = access;
        this.mapper = mapper;
    }

    @GetMapping
    public List<PaymentDtos.BulkFile> list() {
        AuthPrincipal actor = AuthSupport.require();
        if (!actor.isCompanyUser()) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.FORBIDDEN, "Company scope required");
        }
        return fileRepository.findAllForCompany(actor.companyId()).stream()
                .map(f -> mapper.bulkFile(f, lineRepository.findByBulkFileIdOrderByLineNumberAsc(f.getId())))
                .toList();
    }

    @PostMapping
    public PaymentDtos.BulkUploadResult upload(@RequestBody Requests.CreateBulkFileRequest request) {
        AuthPrincipal actor = AuthSupport.require();
        List<BulkFileService.RawLine> rawLines = request.lines() == null ? List.of()
                : request.lines().stream()
                .map(l -> new BulkFileService.RawLine(l.lineNumber(), l.content()))
                .toList();
        BulkFile file = service.upload(actor, request.fileName(), request.declaredTotal(),
                request.declaredCount(), rawLines);
        return new PaymentDtos.BulkUploadResult(file.getId(), file.getRef(), file.getFileName(),
                file.getStatus().name());
    }

    @GetMapping("/{id}")
    public PaymentDtos.BulkFile detail(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        BulkFile file = access.requireBulkFileAccess(actor, id);
        return mapper.bulkFile(file, lineRepository.findByBulkFileIdOrderByLineNumberAsc(file.getId()));
    }
}
