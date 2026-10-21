package com.northbridge.batch;

import com.northbridge.batch.job.BatchScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

/** The batch application context loads with all five jobs registered and the scheduler wired. */
@SpringBootTest
@ActiveProfiles("test")
class BatchContextTest {

    @MockitoBean PaymentApiInternalClient apiClient;
    @MockitoBean CoreClient coreClient;

    @Autowired ApplicationContext context;
    @Autowired BatchScheduler scheduler;

    @Test
    void contextLoadsWithAllJobs() {
        assertNotNull(scheduler);
        String[] names = context.getBeanNamesForType(Job.class);
        assertEquals(5, names.length, "expected the five batch jobs");
        assertTrue(java.util.Arrays.asList(names).containsAll(java.util.List.of(
                "bulkFileProcessingJob", "releaseProcessingJob", "reversalPostingJob",
                "reconciliationJob", "webhookDeliveryJob")));
    }
}
