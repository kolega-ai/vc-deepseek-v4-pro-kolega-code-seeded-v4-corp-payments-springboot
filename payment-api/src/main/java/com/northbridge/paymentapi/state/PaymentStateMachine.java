package com.northbridge.paymentapi.state;

import com.northbridge.paymentapi.domain.PaymentStatus;

import java.util.Map;
import java.util.Set;

/**
 * Payment state machine. This enumerates the ALLOWED transitions as an explicit edge list —
 * anything not listed is forbidden. Every seeded status is produced by one of these transitions.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> EDGES = Map.of(
            PaymentStatus.DRAFT, Set.of(PaymentStatus.PENDING_APPROVAL),
            PaymentStatus.PENDING_APPROVAL, Set.of(
                    PaymentStatus.APPROVED, PaymentStatus.PARTIALLY_APPROVED,
                    PaymentStatus.REJECTED, PaymentStatus.RETURNED),
            PaymentStatus.PARTIALLY_APPROVED, Set.of(
                    PaymentStatus.APPROVED, PaymentStatus.REJECTED, PaymentStatus.RETURNED),
            PaymentStatus.RETURNED, Set.of(PaymentStatus.DRAFT),
            PaymentStatus.APPROVED, Set.of(PaymentStatus.QUEUED_FOR_RELEASE),
            PaymentStatus.QUEUED_FOR_RELEASE, Set.of(PaymentStatus.RELEASED, PaymentStatus.FAILED),
            PaymentStatus.FAILED, Set.of(PaymentStatus.QUEUED_FOR_RELEASE),
            PaymentStatus.RELEASED, Set.of(PaymentStatus.REVERSED),
            PaymentStatus.REJECTED, Set.of(),
            PaymentStatus.REVERSED, Set.of()
    );

    private PaymentStateMachine() {
    }

    public static boolean allows(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> targets = EDGES.get(from);
        return targets != null && targets.contains(to);
    }

    public static void require(PaymentStatus from, PaymentStatus to) {
        if (!allows(from, to)) {
            throw new IllegalStateException("Forbidden payment transition: " + from + " -> " + to);
        }
    }
}
