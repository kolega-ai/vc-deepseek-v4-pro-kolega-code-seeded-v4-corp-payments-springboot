package com.northbridge.paymentapi.web.api;

import com.northbridge.paymentapi.domain.Notification;
import com.northbridge.paymentapi.repo.CompanyRepository;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.repo.NotificationRepository;
import com.northbridge.paymentapi.security.AuthPrincipal;
import com.northbridge.paymentapi.security.AuthSupport;
import com.northbridge.paymentapi.service.NotificationService;
import com.northbridge.paymentapi.web.ViewMapper;
import com.northbridge.paymentapi.web.dto.CommonDtos;
import com.northbridge.paymentapi.web.dto.CompanyDtos;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Current-user profile and notifications. */
@RestController
@RequestMapping("/api")
public class MeController {

    private final NotificationService notifications;
    private final NotificationRepository notificationRepository;
    private final CompanyUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ViewMapper mapper;

    public MeController(NotificationService notifications,
                        NotificationRepository notificationRepository,
                        CompanyUserRepository userRepository,
                        CompanyRepository companyRepository,
                        ViewMapper mapper) {
        this.notifications = notifications;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.mapper = mapper;
    }

    @GetMapping("/me")
    public CompanyDtos.UserProfile me() {
        AuthPrincipal actor = AuthSupport.require();
        var user = userRepository.findById(actor.userId())
                .orElseThrow(() -> new com.northbridge.paymentapi.security.ApiException(
                        com.northbridge.paymentapi.security.ApiException.Code.UNAUTHORIZED, "Unknown user"));
        var company = companyRepository.findById(actor.companyId()).orElse(null);
        return new CompanyDtos.UserProfile(actor.userId(), actor.email(), actor.fullName(),
                actor.role().apiName(), actor.companyId(),
                company == null ? null : company.getLegalName(),
                company == null ? null : company.getBaseCurrency(),
                company == null ? null : company.getStatus().name(),
                actor.approvalLimit(),
                actor.assignedAccountIds() == null ? List.of()
                        : actor.assignedAccountIds().stream().sorted().toList(),
                user.getStatus().name(),
                actor.role() == com.northbridge.paymentapi.domain.Role.COMPANY_MAKER,
                actor.role() == com.northbridge.paymentapi.domain.Role.COMPANY_CHECKER,
                actor.role() == com.northbridge.paymentapi.domain.Role.COMPANY_ADMIN,
                actor.role() == com.northbridge.paymentapi.domain.Role.BANK_OPS,
                actor.role() == com.northbridge.paymentapi.domain.Role.BANK_ADMIN,
                company == null ? null : company.getDailyLimit());
    }

    @GetMapping("/notifications")
    public List<CommonDtos.NotificationView> notifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        AuthPrincipal actor = AuthSupport.require();
        return notifications.recent(actor.userId(), unreadOnly).stream()
                .map(mapper::notification).toList();
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount() {
        AuthPrincipal actor = AuthSupport.require();
        return Map.of("unread", notifications.unreadCount(actor.userId()));
    }

    @PostMapping("/notifications/{id}/read")
    public CommonDtos.NotificationView read(@PathVariable UUID id) {
        AuthPrincipal actor = AuthSupport.require();
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null || !notification.getUser().getId().equals(actor.userId())) {
            throw new com.northbridge.paymentapi.security.ApiException(
                    com.northbridge.paymentapi.security.ApiException.Code.NOT_FOUND, "Notification not found");
        }
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
        return mapper.notification(notification);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Long> readAll() {
        AuthPrincipal actor = AuthSupport.require();
        long marked = 0;
        for (Notification n : notifications.recent(actor.userId(), true)) {
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
            marked++;
        }
        return Map.of("markedRead", marked);
    }
}
