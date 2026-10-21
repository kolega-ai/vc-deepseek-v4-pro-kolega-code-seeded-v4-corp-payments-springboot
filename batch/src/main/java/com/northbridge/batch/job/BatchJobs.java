package com.northbridge.batch.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.Feature;
import com.northbridge.batch.CoreClient;
import com.northbridge.batch.PaymentApiInternalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * The four scheduled batch jobs:
 * <ol>
 *   <li>bulkFileProcessing — parses uploaded bulk CSV files via the decision tier,</li>
 *   <li>releaseProcessing — releases due approved payments and posts approved reversals,</li>
 *   <li>reconciliation — matches released payments against core postings and raises exceptions,</li>
 *   <li>webhookDelivery — posts release events to each company's configured endpoint.</li>
 * </ol>
 */
@Configuration
public class BatchJobs {

    private static final Logger log = LoggerFactory.getLogger(BatchJobs.class);

    private final PaymentApiInternalClient api;
    private final CoreClient core;
    private final RestClient http = RestClient.create();

    public BatchJobs(PaymentApiInternalClient api, CoreClient core) {
        this.api = api;
        this.core = core;
    }

    private Step step(String name, JobRepository jobRepository, PlatformTransactionManager tx,
                      Tasklet tasklet) {
        return new StepBuilder(name, jobRepository).tasklet(tasklet, tx).build();
    }

    // ------------------------------------------------------------------ 1. bulk files

    @Bean
    public Job bulkFileProcessingJob(JobRepository jobRepository, PlatformTransactionManager tx) {
        return new JobBuilder("bulkFileProcessingJob", jobRepository)
                .start(step("bulkFileProcessingStep", jobRepository, tx, (contribution, chunkContext) -> {
                    Map<String, Object> result = api.processBulkFiles();
                    log.info("Bulk file processing run: {}", result);
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    // ------------------------------------------------------------------ 2. releases + reversals

    @Bean
    public Job releaseProcessingJob(JobRepository jobRepository, PlatformTransactionManager tx) {
        return new JobBuilder("releaseProcessingJob", jobRepository)
                .start(step("releaseProcessingStep", jobRepository, tx, (contribution, chunkContext) -> {
                    Map<String, Object> result = api.processReleases();
                    log.info("Release processing run: {}", result);
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    @Bean
    public Job reversalPostingJob(JobRepository jobRepository, PlatformTransactionManager tx) {
        return new JobBuilder("reversalPostingJob", jobRepository)
                .start(step("reversalPostingStep", jobRepository, tx, (contribution, chunkContext) -> {
                    Map<String, Object> result = api.processReversals();
                    log.info("Reversal posting run: {}", result);
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    // ------------------------------------------------------------------ 3. reconciliation

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, PlatformTransactionManager tx) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(step("reconciliationStep", jobRepository, tx, (contribution, chunkContext) -> {
                    List<PaymentApiInternalClient.ReleaseCandidate> candidates = api.reconciliationCandidates();
                    if (candidates.isEmpty()) {
                        return RepeatStatus.FINISHED;
                    }
                    Map<String, CoreClient.Posting> postings = new java.util.HashMap<>();
                    for (CoreClient.Posting posting : core.postings()) {
                        postings.put(posting.coreRef(), posting);
                    }
                    log.info("Reconciliation: {} core postings fetched, {} candidates",
                            postings.size(), candidates.size());
                    List<Map<String, Object>> exceptions = reconcile(candidates, postings);
                    if (!exceptions.isEmpty()) {
                        Map<String, Object> result = api.reportReconciliationExceptions(exceptions);
                        log.warn("Reconciliation raised {} exception(s): {}", exceptions.size(), result);
                    } else {
                        log.info("Reconciliation matched {} release(s) against core postings — clean",
                                candidates.size());
                    }
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    // ------------------------------------------------------------------ 4. webhooks

    /** Pure matching logic: candidates without a core posting, or with a different amount, are exceptions. */
    public static List<Map<String, Object>> reconcile(List<PaymentApiInternalClient.ReleaseCandidate> candidates,
                                               Map<String, CoreClient.Posting> postings) {
        List<Map<String, Object>> exceptions = new java.util.ArrayList<>();
        for (PaymentApiInternalClient.ReleaseCandidate candidate : candidates) {
            CoreClient.Posting posting = postings.get(candidate.coreRef());
            if (posting == null) {
                exceptions.add(Map.of("paymentId", candidate.paymentId(),
                        "coreRef", candidate.coreRef() == null ? "" : candidate.coreRef(),
                        "reason", "core posting missing for released payment"));
            } else if (posting.amount().compareTo(candidate.postedAmount()) != 0) {
                exceptions.add(Map.of("paymentId", candidate.paymentId(),
                        "coreRef", candidate.coreRef(),
                        "reason", "posted amount mismatch: expected " + candidate.postedAmount()
                                + " but core has " + posting.amount()));
            }
        }
        return exceptions;
    }

    /** Posts one release event to the endpoint the company configured and reports what it answered. */
    private String deliver(PaymentApiInternalClient.WebhookDelivery delivery) {
        try {
            org.springframework.http.ResponseEntity<String> response = http.post().uri(delivery.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("event", "payment.released", "paymentRef", delivery.paymentRef()))
                    .retrieve().toEntity(String.class);
            String body = response.getBody();
            // Partner endpoints answer with a small JSON acknowledgement we record for support.
            if (body != null && body.stripLeading().startsWith("{")) {
                Object ack = JSON.parseObject(body, Object.class, Feature.SupportAutoType);
                if (ack instanceof Map<?, ?> map && map.get("statusCode") != null) {
                    return String.valueOf(map.get("statusCode"));
                }
            }
            return String.valueOf(response.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Webhook delivery {} did not complete: {}", delivery.id(), e.getMessage());
            return "000";
        }
    }

    @Bean
    public Job webhookDeliveryJob(JobRepository jobRepository, PlatformTransactionManager tx) {
        return new JobBuilder("webhookDeliveryJob", jobRepository)
                .start(step("webhookDeliveryStep", jobRepository, tx, (contribution, chunkContext) -> {
                    for (PaymentApiInternalClient.WebhookDelivery delivery : api.pendingWebhooks()) {
                        String responseCode = deliver(delivery);
                        api.recordWebhookDelivery(delivery.id(),
                                responseCode.startsWith("2") ? "DELIVERED" : "FAILED", responseCode);
                    }
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }
}
