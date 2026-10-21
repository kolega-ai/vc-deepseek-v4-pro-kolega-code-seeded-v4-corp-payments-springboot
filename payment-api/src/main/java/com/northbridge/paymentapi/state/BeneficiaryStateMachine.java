package com.northbridge.paymentapi.state;

import com.northbridge.paymentapi.domain.BeneficiaryStatus;

import java.util.Map;
import java.util.Set;

/**
 * Beneficiary state machine. pending_cooling→active happens when the cooling period elapses;
 * amending the account number sends active→pending_cooling again. Only ACTIVE is payable.
 */
public final class BeneficiaryStateMachine {

    private static final Map<BeneficiaryStatus, Set<BeneficiaryStatus>> EDGES = Map.of(
            BeneficiaryStatus.PENDING_COOLING, Set.of(BeneficiaryStatus.ACTIVE),
            BeneficiaryStatus.ACTIVE, Set.of(BeneficiaryStatus.SUSPENDED, BeneficiaryStatus.PENDING_COOLING),
            BeneficiaryStatus.SUSPENDED, Set.of(BeneficiaryStatus.ACTIVE)
    );

    private BeneficiaryStateMachine() {
    }

    public static boolean allows(BeneficiaryStatus from, BeneficiaryStatus to) {
        Set<BeneficiaryStatus> targets = EDGES.get(from);
        return targets != null && targets.contains(to);
    }

    public static void require(BeneficiaryStatus from, BeneficiaryStatus to) {
        if (!allows(from, to)) {
            throw new IllegalStateException("Forbidden beneficiary transition: " + from + " -> " + to);
        }
    }
}
