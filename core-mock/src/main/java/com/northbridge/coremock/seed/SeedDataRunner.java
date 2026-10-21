package com.northbridge.coremock.seed;

import com.northbridge.coremock.domain.CoreAccount;
import com.northbridge.coremock.domain.CorePosting;
import com.northbridge.coremock.domain.ForcedFailure;
import com.northbridge.coremock.domain.PaymentStatus;
import com.northbridge.coremock.repo.CoreAccountRepository;
import com.northbridge.coremock.repo.CorePostingRepository;
import com.northbridge.coremock.repo.ForcedFailureRepository;
import com.northbridge.coremock.repo.PaymentStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent seed data for the demo environment. Runs only when the
 * core_account table is empty so restarts never duplicate data. Seeds the four
 * demo companies' accounts, the released postings known to the payment-api
 * tier, their payment_status rows, and the forced-failure records that power
 * the bank-ops retry journey.
 */
@Component
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);
    private static final String FORCED_FAILURE_NOTE = "seeded forced failure: the failure already consumed its forced attempt; the ops retry journey posts successfully";
    private static final String PRODUCT_BUSINESS_CURRENT = "BUSINESS_CURRENT";

    private static final UUID C1 = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID C2 = UUID.fromString("00000000-0000-4000-8000-0000000000c2");
    private static final UUID C3 = UUID.fromString("00000000-0000-4000-8000-0000000000c3");
    private static final UUID C4 = UUID.fromString("00000000-0000-4000-8000-0000000000c4");

    private final CoreAccountRepository accountRepository;
    private final CorePostingRepository postingRepository;
    private final PaymentStatusRepository paymentStatusRepository;
    private final ForcedFailureRepository forcedFailureRepository;

    public SeedDataRunner(CoreAccountRepository accountRepository,
                          CorePostingRepository postingRepository,
                          PaymentStatusRepository paymentStatusRepository,
                          ForcedFailureRepository forcedFailureRepository) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
        this.paymentStatusRepository = paymentStatusRepository;
        this.forcedFailureRepository = forcedFailureRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountRepository.count() > 0) {
            log.info("core-mock seed skipped: core_account table already populated");
            return;
        }
        Instant now = Instant.now();

        seedAccounts();
        seedForcedFailures();
        seedPostings(now);

        log.info("core-mock seed complete: 12 accounts, 21 postings, 2 forced failures");
    }

    private void seedAccounts() {
        account(20301001L, C1, "GBP", "84520.00");
        account(20301002L, C1, "GBP", "12340.55");
        account(20301003L, C1, "GBP", "150.00");
        account(20442011L, C2, "GBP", "156730.10");
        account(20442012L, C2, "GBP", "27800.00");
        account(20442013L, C2, "EUR", "8450.00");
        account(20881021L, C3, "GBP", "642300.00");
        account(20881022L, C3, "GBP", "95120.40");
        account(20881023L, C3, "GBP", "411950.75");
        account(21552031L, C4, "GBP", "2450000.00");
        account(21552032L, C4, "GBP", "3800000.00");
        account(21552033L, C4, "USD", "500000.00");
    }

    private void account(long accountNumber, UUID companyId, String currency, String balance) {
        CoreAccount account = new CoreAccount();
        account.setAccountNumber(String.valueOf(accountNumber));
        account.setCompanyId(companyId);
        account.setCurrency(currency);
        account.setProduct(PRODUCT_BUSINESS_CURRENT);
        account.setBalance(new BigDecimal(balance));
        account.setCreatedAt(Instant.now());
        accountRepository.save(account);
    }

    private void seedForcedFailures() {
        forcedFailure("PY-ACM-1001");
        forcedFailure("PY-DEL-1001");
    }

    private void forcedFailure(String requestRef) {
        ForcedFailure failure = new ForcedFailure();
        failure.setRequestRef(requestRef);
        failure.setAttemptsRemaining(0);
        failure.setNote(FORCED_FAILURE_NOTE);
        forcedFailureRepository.save(failure);
    }

    private void seedPostings(Instant now) {
        posting("CORE-PY-ACM-1010", "PY-ACM-1010", "20301001", "DEBIT", "1251.75", 1, now);
        posting("CORE-PY-ACM-1011", "PY-ACM-1011", "20301001", "DEBIT", "341.34", 1, now);
        posting("CORE-PY-ACM-1012", "PY-ACM-1012", "20301001", "DEBIT", "2182.68", 2, now);
        posting("CORE-PY-ACM-1013", "PY-ACM-1013", "20301002", "DEBIT", "1505.25", 2, now);
        posting("CORE-PY-BRG-1010", "PY-BRG-1010", "20442011", "DEBIT", "12513.00", 1, now);
        posting("CORE-PY-BRG-1011", "PY-BRG-1011", "20442011", "DEBIT", "4755.50", 2, now);
        posting("CORE-PY-BRG-1012", "PY-BRG-1012", "20442011", "DEBIT", "25025.00", 3, now);
        posting("CORE-PY-CRF-1010", "PY-CRF-1010", "20881021", "DEBIT", "48025.00", 1, now);
        posting("CORE-PY-CRF-1011", "PY-CRF-1011", "20881022", "DEBIT", "8348.84", 2, now);
        posting("CORE-PY-CRF-1012", "PY-CRF-1012", "20881021", "DEBIT", "60050.00", 2, now);
        posting("CORE-PY-CRF-1013", "PY-CRF-1013", "20881023", "DEBIT", "99025.00", 4, now);
        posting("CORE-PY-DEL-1010", "PY-DEL-1010", "21552031", "DEBIT", "250025.00", 1, now);
        posting("CORE-PY-DEL-1011", "PY-DEL-1011", "21552031", "DEBIT", "180050.00", 2, now);
        posting("CORE-PY-DEL-1012", "PY-DEL-1012", "21552032", "DEBIT", "95100.00", 3, now);
        posting("CORE-PY-DEL-1013", "PY-DEL-1013", "21552031", "DEBIT", "310025.00", 5, now);
        // Reversal credit restoring the balance for the reversed payment PY-ACM-1014.
        posting("CORE-REV-PY-ACM-1014", "REV-PY-ACM-1014", "20301001", "REVERSAL_CREDIT", "981.48", 1, now);
        // Bulk-file line payments (released through the bulk workflow; request_ref = payment ref).
        posting("CORE-BLK-ACM-0001-L1", "BLK-BULK-ACM-0001-L1", "20301001", "DEBIT", "100.60", 1, now);
        posting("CORE-BLK-ACM-0001-L2", "BLK-BULK-ACM-0001-L2", "20301001", "DEBIT", "200.70", 1, now);
        posting("CORE-BLK-ACM-0001-L3", "BLK-BULK-ACM-0001-L3", "20301002", "DEBIT", "300.80", 1, now);
        posting("CORE-BLK-ACM-0002-L1", "BLK-BULK-ACM-0002-L1", "20301001", "DEBIT", "250.75", 1, now);
        posting("CORE-BLK-ACM-0002-L3", "BLK-BULK-ACM-0002-L3", "20301002", "DEBIT", "120.62", 1, now);
    }

    private void posting(String coreRef, String requestRef, String accountNumber, String postingType,
                         String amount, int daysAgo, Instant now) {
        Instant postedAt = now.minus(Duration.ofDays(daysAgo));
        BigDecimal postingAmount = new BigDecimal(amount);

        CorePosting posting = new CorePosting();
        posting.setCoreRef(coreRef);
        posting.setRequestRef(requestRef);
        posting.setAccountNumber(accountNumber);
        posting.setPostingType(postingType);
        posting.setAmount(postingAmount);
        posting.setCurrency("GBP");
        posting.setCounterparty("SEED");
        posting.setResponseCode("00");
        posting.setStatus("POSTED");
        posting.setPostedAt(postedAt);
        postingRepository.save(posting);

        PaymentStatus status = new PaymentStatus();
        status.setCoreRef(coreRef);
        status.setStatus("POSTED");
        status.setAmount(postingAmount);
        status.setCurrency("GBP");
        status.setPostedAt(postedAt);
        paymentStatusRepository.save(status);
    }
}
