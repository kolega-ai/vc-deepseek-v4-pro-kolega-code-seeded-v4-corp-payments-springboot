package com.northbridge.portal.seed;

import com.northbridge.portal.domain.*;
import com.northbridge.portal.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Portal demo data: the demo users (same ids as the decision tier), a few documents, an API key
 * and a sample outbox email. All demo passwords are documented test values (see README).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class PortalDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PortalDemoSeeder.class);
    public static final String DEMO_PASSWORD = "Password123!";

    private final PortalUserRepository users;
    private final DocumentRepository documents;
    private final ApiKeyRepository apiKeys;
    private final EmailOutboxRepository outbox;
    private final PortalAuditEventRepository audit;
    private final PasswordEncoder passwordEncoder;
    private final Path uploadsDir;

    public PortalDemoSeeder(PortalUserRepository users, DocumentRepository documents,
                            ApiKeyRepository apiKeys, EmailOutboxRepository outbox,
                            PortalAuditEventRepository audit, PasswordEncoder passwordEncoder,
                            @Value("${app.uploads.dir}") String uploadsDir) {
        this.users = users;
        this.documents = documents;
        this.apiKeys = apiKeys;
        this.outbox = outbox;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.uploadsDir = Path.of(uploadsDir).toAbsolutePath();
    }

    // Mirrors of the decision-tier seed ids (each tier keeps its own copy).
    private static final UUID C1 = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID C2 = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID C3 = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID C4 = UUID.fromString("00000000-0000-4000-8000-0000000000c4");
    private static final UUID U1 = UUID.fromString("00000000-0000-4000-8000-000000000071");
    private static final UUID U2 = UUID.fromString("00000000-0000-4000-8000-000000000072");
    private static final UUID U3 = UUID.fromString("00000000-0000-4000-8000-000000000073");
    private static final UUID U4 = UUID.fromString("00000000-0000-4000-8000-000000000074");
    private static final UUID U5 = UUID.fromString("00000000-0000-4000-8000-000000000075");
    private static final UUID U6 = UUID.fromString("00000000-0000-4000-8000-000000000076");
    private static final UUID U7 = UUID.fromString("00000000-0000-4000-8000-000000000077");
    private static final UUID U8 = UUID.fromString("00000000-0000-4000-8000-000000000078");
    private static final UUID U9 = UUID.fromString("00000000-0000-4000-8000-000000000079");
    private static final UUID UA = UUID.fromString("00000000-0000-4000-8000-00000000007a");
    private static final UUID UB = UUID.fromString("00000000-0000-4000-8000-00000000007b");
    private static final UUID UC = UUID.fromString("00000000-0000-4000-8000-00000000007c");
    private static final UUID UD = UUID.fromString("00000000-0000-4000-8000-00000000007d");
    private static final UUID UE = UUID.fromString("00000000-0000-4000-8000-00000000007e");
    private static final UUID UF = UUID.fromString("00000000-0000-4000-8000-00000000007f");
    private static final UUID V1 = UUID.fromString("00000000-0000-4000-8000-000000000081");
    private static final UUID V2 = UUID.fromString("00000000-0000-4000-8000-000000000082");
    private static final UUID V3 = UUID.fromString("00000000-0000-4000-8000-000000000083");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("portal seed skipped: data already present");
            return;
        }
        log.info("Seeding portal demo data...");
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        user(U1, "maker1@acme.example", "Alice Foster", "company_maker", C1, "Acme Industrials Ltd", hash, Set.of());
        user(U2, "maker2@acme.example", "Ben Marsh", "company_maker", C1, "Acme Industrials Ltd", hash,
                Set.of(UUID.fromString("00000000-0000-4000-8000-000000000a02")));
        user(U3, "checker1@acme.example", "Claire Dent", "company_checker", C1, "Acme Industrials Ltd", hash,
                Set.of(), "25000.00");
        user(U4, "checker2@acme.example", "Daniel Osei", "company_checker", C1, "Acme Industrials Ltd", hash,
                Set.of(), "20000.00");
        user(U5, "admin1@acme.example", "Emma Lin", "company_admin", C1, "Acme Industrials Ltd", hash, Set.of());
        user(U6, "maker1@brighton.example", "Fiona Grant", "company_maker", C2, "Brighton Retail Group Plc", hash, Set.of());
        user(U7, "checker1@brighton.example", "George Patel", "company_checker", C2, "Brighton Retail Group Plc",
                hash, Set.of(), "150000.00");
        user(U8, "admin1@brighton.example", "Hannah Reed", "company_admin", C2, "Brighton Retail Group Plc", hash, Set.of());
        user(V3, "invited.user@brighton.example", "Iris Wong", "company_checker", C2, "Brighton Retail Group Plc",
                null, Set.of(UUID.fromString("00000000-0000-4000-8000-000000000a04")), "50000.00", "INVITED");
        user(U9, "maker1@cranfield.example", "Ian Shaw", "company_maker", C3, "Cranfield Logistics Ltd", hash, Set.of());
        user(UA, "checker1@cranfield.example", "Julia Kim", "company_checker", C3, "Cranfield Logistics Ltd", hash,
                Set.of(), "500000.00");
        user(UB, "admin1@cranfield.example", "Karl Weber", "company_admin", C3, "Cranfield Logistics Ltd", hash, Set.of());
        user(UC, "maker1@delos.example", "Laura Nunes", "company_maker", C4, "Delos Energy Holdings Ltd", hash, Set.of());
        user(UD, "checker1@delos.example", "Miguel Santos", "company_checker", C4, "Delos Energy Holdings Ltd",
                hash, Set.of(), "2000000.00");
        user(UE, "admin1@delos.example", "Nadia Hasan", "company_admin", C4, "Delos Energy Holdings Ltd", hash, Set.of());
        user(UF, "ops1@bank.example", "Omar Yusuf", "bank_ops", null, "Northbridge Bank", hash, Set.of());
        user(V1, "ops2@bank.example", "Priya Nair", "bank_ops", null, "Northbridge Bank", hash, Set.of());
        user(V2, "bankadmin1@bank.example", "Quentin Bell", "bank_admin", null, "Northbridge Bank", hash, Set.of());

        seedDocuments();
        seedApiKeys();
        seedOutbox();

        log.info("portal seed complete: 18 users (demo password: {})", DEMO_PASSWORD);
    }

    private void user(UUID id, String email, String name, String role, UUID companyId, String companyName,
                      String passwordHash, Set<UUID> accountIds) {
        user(id, email, name, role, companyId, companyName, passwordHash, accountIds, "0", "ACTIVE");
    }

    private void user(UUID id, String email, String name, String role, UUID companyId, String companyName,
                      String passwordHash, Set<UUID> accountIds, String approvalLimit) {
        user(id, email, name, role, companyId, companyName, passwordHash, accountIds, approvalLimit, "ACTIVE");
    }

    private void user(UUID id, String email, String name, String role, UUID companyId, String companyName,
                      String passwordHash, Set<UUID> accountIds, String approvalLimit, String status) {
        PortalUser user = new PortalUser(id, email, name, role, status, companyId, companyName,
                new BigDecimal(approvalLimit));
        user.setPasswordHash(passwordHash);
        user.setAccountIds(new HashSet<>(accountIds));
        user.setLastLoginAt(Instant.now().minus(1, ChronoUnit.DAYS));
        users.save(user);
    }

    private void seedDocuments() {
        try {
            Files.createDirectories(uploadsDir);
            doc("invoice-8841.pdf", "application/pdf", C1, U1, Document.Category.PAYMENT_SUPPORT,
                    "Supporting invoice for Nordic Trade invoice 8841 — demo content, not a real invoice.\n");
            doc("po-1182.pdf", "application/pdf", C1, U1, Document.Category.PAYMENT_SUPPORT,
                    "Purchase order 1182 for stationery — demo content.\n");
            doc("kyc-acme.txt", "text/plain", C1, U5, Document.Category.IDENTITY_VERIFICATION,
                    "Identity verification summary for Acme Industrials Ltd — demo content.\n");
            doc("errors-BULK-ACM-0002.csv", "text/csv", C1, U1, Document.Category.BULK_ERROR_REPORT,
                    "line_number,error\n2,Beneficiary 99999999 is not registered for this company\n"
                            + "4,Beneficiary Swift Couriers GB is in its cooling period or suspended\n");
        } catch (Exception e) {
            log.warn("Could not write seeded documents: {}", e.getMessage());
        }
    }

    private void doc(String fileName, String mimeType, UUID companyId, UUID uploaderId,
                     Document.Category category, String content) throws Exception {
        String storedName = UUID.randomUUID().toString();
        Files.write(uploadsDir.resolve(storedName), content.getBytes(StandardCharsets.UTF_8));
        Document document = new Document(UUID.randomUUID(), companyId, uploaderId, category, fileName,
                storedName, mimeType, content.length(), null, null);
        document.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        documents.save(document);
    }

    private void seedApiKeys() {
        // Active key for Acme (hash of a documented demo value, shown in the README).
        String demoClear = "nb_live_acme_demo_key_9f3a2c1b";
        ApiKey key = new ApiKey(UUID.randomUUID(), C1, "ERP integration", demoClear.substring(0, 20),
                com.northbridge.portal.service.ApiKeyService.sha256(demoClear), "ACTIVE", U5);
        key.setLastUsedAt(Instant.now().minus(3, ChronoUnit.HOURS));
        apiKeys.save(key);
    }

    private void seedOutbox() {
        outbox.save(new EmailOutbox(UUID.randomUUID(), "invited.user@brighton.example",
                "You're invited to Northbridge Corporate Payments",
                "Hello Iris Wong,\n\nAn administrator invited you to join Brighton Retail Group Plc as "
                        + "company_checker. Accept your invite at /invite/<single-use-token>.\n"));
    }
}
