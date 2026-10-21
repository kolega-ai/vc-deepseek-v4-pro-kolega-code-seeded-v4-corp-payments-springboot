package com.northbridge.portal.service;

import com.northbridge.portal.domain.EmailOutbox;
import com.northbridge.portal.domain.PortalUser;
import com.northbridge.portal.repo.EmailOutboxRepository;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

/**
 * Local email mock. Outbound mail is written to the outbox table and this in-process job marks it
 * sent — no SMTP server, no SaaS dependency. Real delivery is out of scope for this environment.
 */
@Service
public class EmailOutboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository repository;

    public EmailOutboxService(EmailOutboxRepository repository) {
        this.repository = repository;
    }

    /**
     * Renders a company's custom notification wording and queues it to the requesting administrator
     * so they can check how the message will look before it goes out to their colleagues.
     */
    @Transactional
    public String sendTemplatePreview(PortalUser user, String template) {
        VelocityContext context = new VelocityContext();
        context.put("fullName", user.getFullName());
        context.put("companyName", user.getCompanyName());
        context.put("role", user.getRole());
        StringWriter writer = new StringWriter();
        Velocity.evaluate(context, writer, "notification-template", template);
        String rendered = writer.toString();
        EmailOutbox preview = new EmailOutbox(UUID.randomUUID(), user.getEmail(),
                "Notification template preview", rendered);
        repository.save(preview);
        return rendered;
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 10_000)
    @Transactional
    public void deliverPending() {
        int sent = 0;
        for (EmailOutbox email : repository.findTop50ByStatusOrderByCreatedAtAsc("PENDING")) {
            // Mock delivery: record the send. The body is never transmitted anywhere.
            email.setStatus("SENT");
            email.setSentAt(Instant.now());
            repository.save(email);
            sent++;
        }
        if (sent > 0) {
            log.info("Mock email delivery: {} message(s) marked sent", sent);
        }
    }
}
