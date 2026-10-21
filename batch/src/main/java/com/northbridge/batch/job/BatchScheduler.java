package com.northbridge.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process scheduler that drives the Spring Batch jobs. Frequencies are intentionally short for
 * the local demo; the jobs are idempotent and cheap.
 */
@Component
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job bulkFileProcessingJob;
    private final Job releaseProcessingJob;
    private final Job reversalPostingJob;
    private final Job reconciliationJob;
    private final Job webhookDeliveryJob;

    public BatchScheduler(JobLauncher jobLauncher,
                          Job bulkFileProcessingJob,
                          Job releaseProcessingJob,
                          Job reversalPostingJob,
                          Job reconciliationJob,
                          Job webhookDeliveryJob) {
        this.jobLauncher = jobLauncher;
        this.bulkFileProcessingJob = bulkFileProcessingJob;
        this.releaseProcessingJob = releaseProcessingJob;
        this.reversalPostingJob = reversalPostingJob;
        this.reconciliationJob = reconciliationJob;
        this.webhookDeliveryJob = webhookDeliveryJob;
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 8_000)
    public void bulkFiles() {
        run(bulkFileProcessingJob);
    }

    @Scheduled(fixedDelay = 20_000, initialDelay = 12_000)
    public void releases() {
        run(releaseProcessingJob);
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void reversals() {
        run(reversalPostingJob);
    }

    @Scheduled(fixedDelay = 45_000, initialDelay = 25_000)
    public void reconciliation() {
        run(reconciliationJob);
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void webhooks() {
        run(webhookDeliveryJob);
    }

    private void run(Job job) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(job, params);
        } catch (Exception e) {
            log.warn("Job {} failed: {}", job.getName(), e.getMessage());
        }
    }
}
