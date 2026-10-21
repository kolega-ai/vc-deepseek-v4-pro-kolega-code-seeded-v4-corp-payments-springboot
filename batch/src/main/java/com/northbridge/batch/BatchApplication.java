package com.northbridge.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * batch — Spring Batch support module (in-cap, not a tier).
 *
 * <p>Scheduled workflows: bulk-file processing, scheduled release of due payments, reversal
 * posting, core reconciliation and webhook delivery simulation. The module calls the payment-api
 * decision tier over its INTERNAL service endpoints (shared service credential) — it makes no
 * authorization decisions of its own and carries no company scope.</p>
 */
@SpringBootApplication
@EnableBatchProcessing
@EnableScheduling
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
