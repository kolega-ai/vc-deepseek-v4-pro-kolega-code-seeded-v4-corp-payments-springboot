package com.northbridge.coremock.service;

import com.northbridge.coremock.domain.CoreAccount;
import com.northbridge.coremock.domain.CorePosting;
import com.northbridge.coremock.domain.ForcedFailure;
import com.northbridge.coremock.domain.PaymentStatus;
import com.northbridge.coremock.domain.PostingType;
import com.northbridge.coremock.repo.CoreAccountRepository;
import com.northbridge.coremock.repo.CorePostingRepository;
import com.northbridge.coremock.repo.ForcedFailureRepository;
import com.northbridge.coremock.repo.PaymentStatusRepository;
import com.northbridge.coremock.security.ScopeToken;
import com.northbridge.coremock.web.ApiException;
import com.northbridge.coremock.web.dto.PostingRequest;
import com.northbridge.coremock.web.dto.PostingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes postings against core accounts.
 *
 * <p>The full algorithm runs inside one transaction with a pessimistic write
 * lock on the target account row so concurrent postings against the same
 * account are serialized and the balance re-check happens under the lock:
 * <ol>
 *   <li>Idempotency: a posting already recorded for the requestRef is never
 *       executed twice; the original coreRef is returned.</li>
 *   <li>Forced failure: a configured failure for the requestRef is consumed
 *       (attempts decremented) and declined with response code 91 — no posting
 *       row is written and no balance changes.</li>
 *   <li>Balance check under the lock: debits require sufficient funds.</li>
 *   <li>The balance is updated, a posting row is inserted and the payment
 *       status record is upserted.</li>
 * </ol>
 *
 * <p>Authorization: the request body's account number is untrusted. It must be
 * covered by the verified scope token (explicitly listed for a company scope,
 * or a bank scope). Logging is limited to request_ref/core_ref/account number —
 * never balances or credentials.
 */
@Service
public class PostingService {

    private static final Logger log = LoggerFactory.getLogger(PostingService.class);
    private static final String POSTED = "POSTED";
    private static final String RESPONSE_OK = "00";

    private final CoreAccountRepository accountRepository;
    private final CorePostingRepository postingRepository;
    private final PaymentStatusRepository paymentStatusRepository;
    private final ForcedFailureRepository forcedFailureRepository;

    public PostingService(CoreAccountRepository accountRepository,
                          CorePostingRepository postingRepository,
                          PaymentStatusRepository paymentStatusRepository,
                          ForcedFailureRepository forcedFailureRepository) {
        this.accountRepository = accountRepository;
        this.postingRepository = postingRepository;
        this.paymentStatusRepository = paymentStatusRepository;
        this.forcedFailureRepository = forcedFailureRepository;
    }

    @Transactional
    public PostingResponse post(ScopeToken scopeToken, PostingRequest request) {
        validateRequest(request);
        scopeToken.requireAccountAccess(request.accountNumber());

        // Idempotency: never post the same requestRef twice.
        Optional<CorePosting> existing = postingRepository.findByRequestRef(request.requestRef());
        if (existing.isPresent()) {
            log.info("posting idempotency hit: request_ref {} already posted as {}",
                    request.requestRef(), existing.get().getCoreRef());
            return PostingResponse.duplicate(existing.get().getCoreRef());
        }

        // Forced failure injection for the demo retry journey.
        Optional<ForcedFailure> forced = forcedFailureRepository.findByRequestRef(request.requestRef());
        if (forced.isPresent() && forced.get().getAttemptsRemaining() > 0) {
            ForcedFailure failure = forced.get();
            failure.setAttemptsRemaining(failure.getAttemptsRemaining() - 1);
            forcedFailureRepository.save(failure);
            log.info("posting forced failure: request_ref {} declined, attempts_remaining {}",
                    request.requestRef(), failure.getAttemptsRemaining());
            return PostingResponse.forcedFailure();
        }

        // Lock the account row for the duration of the transaction and re-check
        // the balance under the lock.
        CoreAccount account = accountRepository.findForUpdateByAccountNumber(request.accountNumber())
                .orElseThrow(ApiException::accountNotFound);
        scopeToken.requireCompany(account.getCompanyId());

        // Defense-in-depth idempotency re-check under the lock: two concurrent
        // requests for the same requestRef cannot both create a posting.
        Optional<CorePosting> racing = postingRepository.findByRequestRef(request.requestRef());
        if (racing.isPresent()) {
            log.info("posting idempotency hit (under lock): request_ref {} already posted as {}",
                    request.requestRef(), racing.get().getCoreRef());
            return PostingResponse.duplicate(racing.get().getCoreRef());
        }

        PostingType type = PostingType.fromRequest(request.postingType());
        if (type == null) {
            throw ApiException.validation("postingType must be one of DEBIT, CREDIT, REVERSAL_CREDIT, REVERSAL_DEBIT");
        }

        if (type.isDebit() && account.getBalance().compareTo(request.amount()) < 0) {
            log.info("posting declined INSUFFICIENT_FUNDS: request_ref {} account {}",
                    request.requestRef(), request.accountNumber());
            return PostingResponse.insufficientFunds();
        }

        BigDecimal newBalance = type.isDebit()
                ? account.getBalance().subtract(request.amount())
                : account.getBalance().add(request.amount());
        account.setBalance(newBalance);

        String coreRef = newCoreRef();
        Instant postedAt = Instant.now();

        CorePosting posting = new CorePosting();
        posting.setCoreRef(coreRef);
        posting.setRequestRef(request.requestRef());
        posting.setAccountNumber(request.accountNumber());
        posting.setPostingType(type.name());
        posting.setAmount(request.amount());
        posting.setCurrency(request.currency());
        posting.setCounterparty(request.counterparty());
        posting.setResponseCode(RESPONSE_OK);
        posting.setStatus(POSTED);
        posting.setPostedAt(postedAt);
        postingRepository.save(posting);

        upsertPaymentStatus(coreRef, POSTED, request.amount(), request.currency(), postedAt);

        log.info("posting {} for account {} request_ref {} posted", coreRef, request.accountNumber(), request.requestRef());
        return PostingResponse.posted(coreRef, newBalance);
    }

    private void upsertPaymentStatus(String coreRef, String status, BigDecimal amount, String currency, Instant postedAt) {
        PaymentStatus paymentStatus = paymentStatusRepository.findByCoreRef(coreRef).orElseGet(PaymentStatus::new);
        paymentStatus.setCoreRef(coreRef);
        paymentStatus.setStatus(status);
        paymentStatus.setAmount(amount);
        paymentStatus.setCurrency(currency);
        paymentStatus.setPostedAt(postedAt);
        paymentStatusRepository.save(paymentStatus);
    }

    /**
     * Core references are "CORE-" plus the first 8 characters of a random UUID,
     * uppercased. Collisions are astronomically unlikely and the unique
     * constraint on core_ref is the final backstop.
     */
    private static String newCoreRef() {
        return "CORE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static void validateRequest(PostingRequest request) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw ApiException.validation("amount must be greater than zero");
        }
        if (request.amount().scale() > 4) {
            throw ApiException.validation("amount must not exceed 4 decimal places");
        }
        if (request.amount().precision() - request.amount().scale() > 15) {
            throw ApiException.validation("amount has too many integer digits");
        }
        if (request.currency() == null || !request.currency().matches("[A-Z]{3}")) {
            throw ApiException.validation("currency must be 3 uppercase letters");
        }
    }
}
