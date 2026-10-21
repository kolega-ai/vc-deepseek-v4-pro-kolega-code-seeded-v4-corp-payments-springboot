package com.northbridge.paymentapi.state;

import com.northbridge.paymentapi.domain.BulkLineStatus;

import java.util.Map;
import java.util.Set;

/** Bulk file line state machine: valid→approved→released; valid→rejected; invalid is terminal. */
public final class BulkLineStateMachine {

    private static final Map<BulkLineStatus, Set<BulkLineStatus>> EDGES = Map.of(
            BulkLineStatus.VALID, Set.of(BulkLineStatus.APPROVED, BulkLineStatus.REJECTED),
            BulkLineStatus.APPROVED, Set.of(BulkLineStatus.RELEASED),
            BulkLineStatus.RELEASED, Set.of(),
            BulkLineStatus.REJECTED, Set.of(),
            BulkLineStatus.INVALID, Set.of()
    );

    private BulkLineStateMachine() {
    }

    public static boolean allows(BulkLineStatus from, BulkLineStatus to) {
        Set<BulkLineStatus> targets = EDGES.get(from);
        return targets != null && targets.contains(to);
    }

    public static void require(BulkLineStatus from, BulkLineStatus to) {
        if (!allows(from, to)) {
            throw new IllegalStateException("Forbidden bulk line transition: " + from + " -> " + to);
        }
    }
}
