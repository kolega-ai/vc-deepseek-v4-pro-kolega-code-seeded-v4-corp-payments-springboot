package com.northbridge.paymentapi.state;

import com.northbridge.paymentapi.domain.BulkFileStatus;

import java.util.Map;
import java.util.Set;

/** Bulk file state machine: allowed transitions only. */
public final class BulkFileStateMachine {

    private static final Map<BulkFileStatus, Set<BulkFileStatus>> EDGES = Map.of(
            BulkFileStatus.UPLOADED, Set.of(BulkFileStatus.PARSING),
            BulkFileStatus.PARSING, Set.of(
                    BulkFileStatus.PARSED, BulkFileStatus.PARSED_WITH_ERRORS, BulkFileStatus.REJECTED),
            BulkFileStatus.PARSED, Set.of(BulkFileStatus.LINES_RELEASED),
            BulkFileStatus.PARSED_WITH_ERRORS, Set.of(BulkFileStatus.LINES_RELEASED),
            BulkFileStatus.LINES_RELEASED, Set.of(),
            BulkFileStatus.REJECTED, Set.of()
    );

    private BulkFileStateMachine() {
    }

    public static boolean allows(BulkFileStatus from, BulkFileStatus to) {
        Set<BulkFileStatus> targets = EDGES.get(from);
        return targets != null && targets.contains(to);
    }

    public static void require(BulkFileStatus from, BulkFileStatus to) {
        if (!allows(from, to)) {
            throw new IllegalStateException("Forbidden bulk file transition: " + from + " -> " + to);
        }
    }
}
