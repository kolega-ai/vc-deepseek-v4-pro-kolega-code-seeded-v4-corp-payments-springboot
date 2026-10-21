package com.northbridge.batch;

import com.northbridge.batch.job.BatchJobs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Reconciliation matching: missing postings and amount mismatches become exceptions. */
class ReconciliationLogicTest {

    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID p3 = UUID.randomUUID();

    @Test
    void matchesPostingsAndFlagsMismatches() {
        var candidates = List.of(
                new PaymentApiInternalClient.ReleaseCandidate(p1, "PY-1", "CORE-1", new BigDecimal("100.00"), "20301001"),
                new PaymentApiInternalClient.ReleaseCandidate(p2, "PY-2", "CORE-2", new BigDecimal("200.00"), "20301001"),
                new PaymentApiInternalClient.ReleaseCandidate(p3, "PY-3", "CORE-3", new BigDecimal("300.00"), "20301001"));
        var postings = Map.of(
                "CORE-1", new CoreClient.Posting("CORE-1", "PY-1", "20301001", "DEBIT",
                        new BigDecimal("100.00"), "GBP", "2026-09-01T00:00:00Z"),
                // CORE-2 missing entirely, CORE-3 amount differs
                "CORE-3", new CoreClient.Posting("CORE-3", "PY-3", "20301001", "DEBIT",
                        new BigDecimal("999.99"), "GBP", "2026-09-01T00:00:00Z"));

        List<Map<String, Object>> exceptions = BatchJobs.reconcile(candidates, postings);

        assertEquals(2, exceptions.size());
        assertTrue(exceptions.stream().anyMatch(e -> p2.equals(e.get("paymentId"))
                && ((String) e.get("reason")).contains("missing")));
        assertTrue(exceptions.stream().anyMatch(e -> p3.equals(e.get("paymentId"))
                && ((String) e.get("reason")).contains("mismatch")));
        assertTrue(exceptions.stream().noneMatch(e -> p1.equals(e.get("paymentId"))));
    }
}
