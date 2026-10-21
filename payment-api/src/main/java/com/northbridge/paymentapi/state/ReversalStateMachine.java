package com.northbridge.paymentapi.state;

import com.northbridge.paymentapi.domain.ReversalStatus;

import java.util.Map;
import java.util.Set;

/** Reversal state machine: requested→approved (bank_admin); requested→declined; approved→posted. */
public final class ReversalStateMachine {

    private static final Map<ReversalStatus, Set<ReversalStatus>> EDGES = Map.of(
            ReversalStatus.REQUESTED, Set.of(ReversalStatus.APPROVED, ReversalStatus.DECLINED),
            ReversalStatus.APPROVED, Set.of(ReversalStatus.POSTED),
            ReversalStatus.POSTED, Set.of(),
            ReversalStatus.DECLINED, Set.of()
    );

    private ReversalStateMachine() {
    }

    public static boolean allows(ReversalStatus from, ReversalStatus to) {
        Set<ReversalStatus> targets = EDGES.get(from);
        return targets != null && targets.contains(to);
    }

    public static void require(ReversalStatus from, ReversalStatus to) {
        if (!allows(from, to)) {
            throw new IllegalStateException("Forbidden reversal transition: " + from + " -> " + to);
        }
    }
}
