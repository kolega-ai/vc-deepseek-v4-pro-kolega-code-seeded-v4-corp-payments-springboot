package com.northbridge.portal.service;

import com.northbridge.portal.client.Api;
import com.northbridge.portal.client.PaymentApiClient;
import com.northbridge.portal.domain.*;
import com.northbridge.portal.repo.*;
import com.northbridge.portal.security.ApiException;
import com.northbridge.portal.security.AuthContext;
import com.northbridge.portal.web.ProfileDetailsForm;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Account lifecycle on the channel tier: invite-based onboarding (single-use, time-bound tokens),
 * password reset (single-use, time-bound tokens), password change and profile update. Email is
 * written to the local outbox (mock — no SaaS).
 */
@Service
public class UserLifecycleService {

    private static final long INVITE_TTL_HOURS = 168;   // 7 days
    private static final long RESET_TTL_MINUTES = 30;   // 30 minutes

    private final PortalUserRepository users;
    private final InviteTokenRepository inviteTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final EmailOutboxRepository outbox;
    private final PortalAuditService auditService;
    private final PaymentApiClient paymentApiClient;
    private final AuthContext auth;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserLifecycleService(PortalUserRepository users,
                                InviteTokenRepository inviteTokens,
                                PasswordResetTokenRepository resetTokens,
                                EmailOutboxRepository outbox,
                                PortalAuditService auditService,
                                PaymentApiClient paymentApiClient,
                                AuthContext auth,
                                PasswordEncoder passwordEncoder) {
        this.users = users;
        this.inviteTokens = inviteTokens;
        this.resetTokens = resetTokens;
        this.outbox = outbox;
        this.auditService = auditService;
        this.paymentApiClient = paymentApiClient;
        this.auth = auth;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Records a payment-api invite result as a portal user plus a single-use invite token, and
     * queues the invite email (mock outbox).
     */
    @Transactional
    public InviteToken recordInvite(Api.CompanyUser invited, String companyName) {
        PortalUser user = users.findById(invited.id()).orElseGet(() -> {
            PortalUser created = new PortalUser(invited.id(), invited.email(), invited.fullName(),
                    invited.role(), "INVITED", auth.currentCompanyId(), companyName,
                    invited.approvalLimit());
            created.setAccountIds(new java.util.HashSet<>(invited.accountIds()));
            return users.save(created);
        });
        String raw = randomToken();
        InviteToken token = new InviteToken(UUID.randomUUID(), ApiKeyService.sha256(raw), user.getId(),
                user.getEmail(), user.getCompanyId(), companyName, user.getRole(),
                Instant.now().plus(INVITE_TTL_HOURS, ChronoUnit.HOURS));
        inviteTokens.save(token);
        queueEmail(user.getEmail(), "You're invited to Northbridge Corporate Payments",
                "Hello " + user.getFullName() + ",\n\nAn administrator invited you to join "
                        + companyName + " on the Corporate Payments Portal as " + user.getRole()
                        + ".\nAccept your invite here: /invite/" + raw
                        + "\n\nThe link expires in 7 days.");
        auditService.audit("INVITE_SENT", "user", user.getEmail(),
                "Invite sent to " + user.getEmail() + " as " + user.getRole());
        return token;
    }

    /** Records a bank-admin company-admin invite (same lifecycle, bank actor). */
    @Transactional
    public InviteToken recordBankInvite(UUID companyId, Api.CompanyUser invited, String companyName) {
        PortalUser user = users.findById(invited.id()).orElseGet(() ->
                users.save(new PortalUser(invited.id(), invited.email(), invited.fullName(),
                        invited.role(), "INVITED", companyId, companyName, java.math.BigDecimal.ZERO)));
        user.setCompanyId(companyId);
        users.save(user);
        String raw = randomToken();
        InviteToken token = new InviteToken(UUID.randomUUID(), ApiKeyService.sha256(raw), user.getId(),
                user.getEmail(), companyId, companyName, user.getRole(),
                Instant.now().plus(INVITE_TTL_HOURS, ChronoUnit.HOURS));
        inviteTokens.save(token);
        queueEmail(user.getEmail(), "You're invited to Northbridge Corporate Payments",
                "Hello " + user.getFullName() + ",\n\nNorthbridge Bank has onboarded " + companyName
                        + " on the Corporate Payments Portal and invited you as company_admin.\n"
                        + "Accept your invite here: /invite/" + raw
                        + "\n\nThe link expires in 7 days.");
        auditService.audit("COMPANY_ADMIN_INVITE_SENT", "user", user.getEmail(),
                "Company-admin invite sent to " + user.getEmail() + " for " + companyName);
        return token;
    }

    /** Validates an invite token and returns it when valid (single-use, time-bound). */
    @Transactional(readOnly = true)
    public InviteToken validateInvite(String rawToken) {
        InviteToken token = inviteTokens.findByTokenHash(ApiKeyService.sha256(rawToken))
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Invite link is invalid"));
        if (token.getUsedAt() != null) {
            throw new ApiException("VALIDATION", "This invite has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("VALIDATION", "This invite has expired");
        }
        return token;
    }

    /** Accepts an invite: sets the password, activates the portal user and the decision tier user. */
    @Transactional
    public PortalUser acceptInvite(String rawToken, String password, String fullName) {
        InviteToken token = validateInvite(rawToken);
        validatePassword(password);
        PortalUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Invited user not found"));
        user.setPasswordHash(passwordEncoder.encode(password));
        if (fullName != null && !fullName.isBlank() && fullName.length() <= 200) {
            user.setFullName(fullName.strip());
        }
        user.setStatus("ACTIVE");
        user.setUpdatedAt(Instant.now());
        users.save(user);
        token.setUsedAt(Instant.now());
        inviteTokens.save(token);
        // Activate the mirrored user in the decision tier (service-credential internal call).
        paymentApiClient.activateUser(user.getId());
        auditService.auditSystem(user.getEmail(), user.getRole(), user.getCompanyId(),
                "INVITE_ACCEPTED", "user", user.getEmail(), "Invite accepted; account activated");
        return user;
    }

    // ------------------------------------------------------------------ password reset

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || !email.matches("^[^@\\s]{1,64}@[^@\\s]{1,255}$")) {
            throw new ApiException("VALIDATION", "Enter a valid email address");
        }
        PortalUser user = users.findByEmailIgnoreCase(email.strip()).orElse(null);
        if (user == null) {
            throw new ApiException("NOT_FOUND", "No account is registered with that address");
        }
        if (!user.isActive()) {
            return;
        }
        String raw = randomToken();
        resetTokens.save(new PasswordResetToken(UUID.randomUUID(), ApiKeyService.sha256(raw),
                user.getId(), Instant.now().plus(RESET_TTL_MINUTES, ChronoUnit.MINUTES)));
        queueEmail(user.getEmail(), "Reset your Northbridge password",
                "Hello " + user.getFullName() + ",\n\nUse this link to reset your password: "
                        + "/reset-password/" + raw + "\n\nThe link is single-use and expires in 30 minutes.");
        auditService.auditSystem(user.getEmail(), user.getRole(), user.getCompanyId(),
                "PASSWORD_RESET_REQUESTED", "user", user.getEmail(), "Password reset requested");
    }

    @Transactional(readOnly = true)
    public PasswordResetToken validateResetToken(String rawToken) {
        PasswordResetToken token = resetTokens.findByTokenHash(ApiKeyService.sha256(rawToken))
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Reset link is invalid"));
        if (token.getUsedAt() != null) {
            throw new ApiException("VALIDATION", "This reset link has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("VALIDATION", "This reset link has expired");
        }
        return token;
    }

    /** Resets a password with a single-use token; the token is consumed even on failure to use. */
    @Transactional
    public void resetPassword(String rawToken, String password) {
        PasswordResetToken token = validateResetToken(rawToken);
        validatePassword(password);
        PortalUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "User not found"));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUpdatedAt(Instant.now());
        users.save(user);
        token.setUsedAt(Instant.now());
        resetTokens.save(token);
        auditService.auditSystem(user.getEmail(), user.getRole(), user.getCompanyId(),
                "PASSWORD_RESET_COMPLETED", "user", user.getEmail(), "Password reset completed");
    }

    // ------------------------------------------------------------------ profile

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        PortalUser user = auth.requireUser();
        boolean signedInRecently = user.getLastLoginAt() != null
                && user.getLastLoginAt().isAfter(Instant.now().minus(30, ChronoUnit.MINUTES));
        if (!signedInRecently && (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new ApiException("VALIDATION", "Current password is incorrect");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        users.save(user);
        auditService.audit("PASSWORD_CHANGED", "user", user.getEmail(), "Password changed");
    }

    @Transactional
    public void updateProfile(ProfileDetailsForm form) {
        PortalUser user = auth.requireUser();
        String fullName = form.getFullName();
        if (fullName == null || fullName.isBlank() || fullName.length() > 200) {
            throw new ApiException("VALIDATION", "Full name is required (max 200)");
        }
        form.setFullName(fullName.strip());
        BeanUtils.copyProperties(form, user, unsubmitted(form));
        user.setUpdatedAt(Instant.now());
        users.save(user);
        auditService.audit("PROFILE_UPDATED", "user", user.getEmail(), "Profile updated");
    }

    /** Properties the form did not carry, so an untouched field keeps its stored value. */
    private static String[] unsubmitted(ProfileDetailsForm form) {
        BeanWrapper wrapper = new BeanWrapperImpl(form);
        return java.util.Arrays.stream(wrapper.getPropertyDescriptors())
                .map(java.beans.PropertyDescriptor::getName)
                .filter(name -> wrapper.isReadableProperty(name) && wrapper.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }

    // ------------------------------------------------------------------ batch signing phrase

    private static final byte[] SIGNING_SALT = "northbridge-batch-sign".getBytes(StandardCharsets.UTF_8);

    /** Sets the phrase a maker uses to sign the bulk instructions they submit. */
    @Transactional
    public void setSigningPhrase(String phrase) {
        PortalUser user = auth.requireUser();
        if (phrase == null || phrase.length() < 8 || phrase.length() > 128) {
            throw new ApiException("VALIDATION", "A signing phrase of 8-128 characters is required");
        }
        user.setSigningDigest(deriveSigningDigest(phrase));
        user.setUpdatedAt(Instant.now());
        users.save(user);
        auditService.audit("SIGNING_PHRASE_SET", "user", user.getEmail(), "Signing phrase set");
    }

    /** Confirms a supplied phrase against the stored signing digest. */
    @Transactional(readOnly = true)
    public boolean matchesSigningPhrase(String phrase) {
        PortalUser user = auth.requireUser();
        String stored = user.getSigningDigest();
        if (stored == null || phrase == null) {
            return false;
        }
        byte[] presented = Base64.getDecoder().decode(deriveSigningDigest(phrase));
        byte[] expected = Base64.getDecoder().decode(stored);
        return MessageDigest.isEqual(presented, expected);
    }

    private static String deriveSigningDigest(String phrase) {
        try {
            PBEKeySpec spec = new PBEKeySpec(phrase.toCharArray(), SIGNING_SALT, 1024, 128);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] digest = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive the signing digest", e);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void validatePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new ApiException("VALIDATION", "Password must be at least 12 characters");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9].*")) {
            throw new ApiException("VALIDATION", "Password must contain letters and numbers");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void queueEmail(String to, String subject, String body) {
        outbox.save(new EmailOutbox(UUID.randomUUID(), to, subject, body));
    }
}
