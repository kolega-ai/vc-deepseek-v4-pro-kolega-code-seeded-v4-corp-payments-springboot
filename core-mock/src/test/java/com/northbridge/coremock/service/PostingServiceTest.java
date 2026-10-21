package com.northbridge.coremock.service;

import com.northbridge.coremock.domain.CoreAccount;
import com.northbridge.coremock.domain.ForcedFailure;
import com.northbridge.coremock.repo.CoreAccountRepository;
import com.northbridge.coremock.repo.CorePostingRepository;
import com.northbridge.coremock.repo.ForcedFailureRepository;
import com.northbridge.coremock.repo.PaymentStatusRepository;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.web.dto.PostingRequest;
import com.northbridge.coremock.web.dto.PostingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posting execution against embedded H2 (Flyway disabled; Hibernate
 * create-drop). Exercises the money movements, the forced-failure journey and
 * the idempotency guarantee.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostingService.class)
class PostingServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final String ACCOUNT_NUMBER = "20301001";

    @Autowired
    private PostingService postingService;

    @Autowired
    private CoreAccountRepository accountRepository;

    @Autowired
    private CorePostingRepository postingRepository;

    @Autowired
    private ForcedFailureRepository forcedFailureRepository;

    @Autowired
    private PaymentStatusRepository paymentStatusRepository;

    private ScopeToken bankScope;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        bankScope = new ScopeToken("test-subject", now.getEpochSecond(),
                now.plusSeconds(300).getEpochSecond(), ScopeToken.Scope.bank());
    }

    private CoreAccount accountWithBalance(String balance) {
        CoreAccount account = new CoreAccount();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setCompanyId(COMPANY_ID);
        account.setCurrency("GBP");
        account.setProduct("BUSINESS_CURRENT");
        account.setBalance(new BigDecimal(balance));
        account.setCreatedAt(Instant.now());
        return accountRepository.saveAndFlush(account);
    }

    private PostingRequest debit(String requestRef, String amount) {
        return new PostingRequest(requestRef, ACCOUNT_NUMBER, "DEBIT", new BigDecimal(amount), "GBP", "TEST");
    }

    @Test
    void insufficientFundsReturnsResponseCode51AndDoesNotPost() {
        accountWithBalance("100.00");

        PostingResponse response = postingService.post(bankScope, debit("REQ-INSF-1", "150.00"));

        assertThat(response.coreRef()).isNull();
        assertThat(response.status()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.responseCode()).isEqualTo("51");
        assertThat(postingRepository.count()).isZero();
        assertThat(accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void forcedFailureDecrementsAttemptsAndSecondAttemptSucceeds() {
        accountWithBalance("1000.00");
        ForcedFailure failure = new ForcedFailure();
        failure.setRequestRef("REQ-FORCED-1");
        failure.setAttemptsRemaining(1);
        failure.setNote("seeded forced failure for demo retry journey");
        forcedFailureRepository.saveAndFlush(failure);

        PostingResponse first = postingService.post(bankScope, debit("REQ-FORCED-1", "100.00"));
        assertThat(first.status()).isEqualTo("FORCED_FAILURE");
        assertThat(first.responseCode()).isEqualTo("91");
        assertThat(first.coreRef()).isNull();
        assertThat(first.message()).isEqualTo("Forced decline");
        assertThat(forcedFailureRepository.findByRequestRef("REQ-FORCED-1").orElseThrow().getAttemptsRemaining())
                .isZero();
        assertThat(postingRepository.count()).isZero();

        PostingResponse second = postingService.post(bankScope, debit("REQ-FORCED-1", "100.00"));
        assertThat(second.status()).isEqualTo("POSTED");
        assertThat(second.responseCode()).isEqualTo("00");
        assertThat(second.coreRef()).isNotNull().startsWith("CORE-");
        assertThat(second.balance()).isEqualByComparingTo("900.00");
        assertThat(postingRepository.countByRequestRef("REQ-FORCED-1")).isEqualTo(1);
        assertThat(paymentStatusRepository.findByCoreRef(second.coreRef())).isPresent();
    }

    @Test
    void duplicateRequestRefReturnsSameCoreRefAndPostsOnlyOnce() {
        accountWithBalance("1000.00");

        PostingResponse first = postingService.post(bankScope, debit("REQ-DUP-1", "50.00"));
        assertThat(first.status()).isEqualTo("POSTED");

        PostingResponse second = postingService.post(bankScope, debit("REQ-DUP-1", "50.00"));

        assertThat(second.status()).isEqualTo("POSTED");
        assertThat(second.duplicate()).isTrue();
        assertThat(second.coreRef()).isEqualTo(first.coreRef());
        assertThat(postingRepository.countByRequestRef("REQ-DUP-1")).isEqualTo(1);
        assertThat(accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow().getBalance())
                .isEqualByComparingTo("950.00");
    }

    @Test
    void debitReducesBalance() {
        accountWithBalance("1000.00");

        PostingResponse response = postingService.post(bankScope, debit("REQ-DEBIT-1", "250.00"));

        assertThat(response.status()).isEqualTo("POSTED");
        assertThat(response.balance()).isEqualByComparingTo("750.00");
        assertThat(accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow().getBalance())
                .isEqualByComparingTo("750.00");
    }

    @Test
    void creditIncreasesBalance() {
        accountWithBalance("1000.00");

        PostingResponse response = postingService.post(bankScope,
                new PostingRequest("REQ-CREDIT-1", ACCOUNT_NUMBER, "CREDIT", new BigDecimal("250.00"), "GBP", "TEST"));

        assertThat(response.status()).isEqualTo("POSTED");
        assertThat(response.balance()).isEqualByComparingTo("1250.00");
        assertThat(accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow().getBalance())
                .isEqualByComparingTo("1250.00");
    }

    @Test
    void zeroOrNegativeAmountIsRejected() {
        accountWithBalance("1000.00");

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                com.northbridge.coremock.web.ApiException.class,
                () -> postingService.post(bankScope, debit("REQ-ZERO-1", "0.00"))).getCode())
                .isEqualTo("VALIDATION");
        assertThat(postingRepository.count()).isZero();
    }
}
