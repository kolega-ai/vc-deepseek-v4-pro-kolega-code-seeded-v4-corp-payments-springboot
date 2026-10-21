package com.northbridge.paymentapi.service;

import com.northbridge.paymentapi.domain.CompanyUser;
import com.northbridge.paymentapi.domain.Notification;
import com.northbridge.paymentapi.domain.NotificationType;
import com.northbridge.paymentapi.domain.Role;
import com.northbridge.paymentapi.domain.UserStatus;
import com.northbridge.paymentapi.repo.CompanyUserRepository;
import com.northbridge.paymentapi.repo.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** In-app notifications for workflow events, delivered to named users in this tier's database. */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final CompanyUserRepository userRepository;

    public NotificationService(NotificationRepository repository, CompanyUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public void notify(UUID userId, NotificationType type, String message, String link) {
        userRepository.findById(userId).ifPresent(user ->
                repository.save(new Notification(UUID.randomUUID(), user, type, message, link)));
    }

    public void notifyCompanyRole(UUID companyId, Role role, NotificationType type, String message, String link) {
        for (CompanyUser user : userRepository.findByCompanyIdAndRoleAndStatus(companyId, role, UserStatus.ACTIVE)) {
            repository.save(new Notification(UUID.randomUUID(), user, type, message, link));
        }
    }

    /** Notify every active user with the given role across the bank. */
    public void notifyBankUsers(Role role, NotificationType type, String message, String link) {
        userRepository.findAll().stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getRole() == role)
                .forEach(u -> repository.save(new Notification(UUID.randomUUID(), u, type, message, link)));
    }

    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    public List<Notification> recent(UUID userId, boolean unreadOnly) {
        return unreadOnly
                ? repository.findTop50ByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId)
                : repository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }
}
