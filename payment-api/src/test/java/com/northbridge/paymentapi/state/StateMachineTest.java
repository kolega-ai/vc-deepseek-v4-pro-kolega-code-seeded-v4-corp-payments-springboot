package com.northbridge.paymentapi.state;

import com.northbridge.paymentapi.domain.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies each state machine against the allowed-edge lists from the spec. */
class StateMachineTest {

    @Test
    void paymentEdgesExactlyAsSpecified() {
        assertTrue(PaymentStateMachine.allows(PaymentStatus.DRAFT, PaymentStatus.PENDING_APPROVAL));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PENDING_APPROVAL, PaymentStatus.APPROVED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PENDING_APPROVAL, PaymentStatus.PARTIALLY_APPROVED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PENDING_APPROVAL, PaymentStatus.REJECTED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PENDING_APPROVAL, PaymentStatus.RETURNED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PARTIALLY_APPROVED, PaymentStatus.APPROVED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PARTIALLY_APPROVED, PaymentStatus.REJECTED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.PARTIALLY_APPROVED, PaymentStatus.RETURNED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.RETURNED, PaymentStatus.DRAFT));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.APPROVED, PaymentStatus.QUEUED_FOR_RELEASE));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.QUEUED_FOR_RELEASE, PaymentStatus.RELEASED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.QUEUED_FOR_RELEASE, PaymentStatus.FAILED));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.FAILED, PaymentStatus.QUEUED_FOR_RELEASE));
        assertTrue(PaymentStateMachine.allows(PaymentStatus.RELEASED, PaymentStatus.REVERSED));

        // Forbidden edges
        assertFalse(PaymentStateMachine.allows(PaymentStatus.DRAFT, PaymentStatus.APPROVED));
        assertFalse(PaymentStateMachine.allows(PaymentStatus.RELEASED, PaymentStatus.FAILED));
        assertFalse(PaymentStateMachine.allows(PaymentStatus.REJECTED, PaymentStatus.PENDING_APPROVAL));
        assertFalse(PaymentStateMachine.allows(PaymentStatus.REVERSED, PaymentStatus.RELEASED));
        assertFalse(PaymentStateMachine.allows(PaymentStatus.APPROVED, PaymentStatus.RELEASED));
        assertFalse(PaymentStateMachine.allows(PaymentStatus.FAILED, PaymentStatus.RELEASED));
    }

    @Test
    void bulkFileEdges() {
        assertTrue(BulkFileStateMachine.allows(BulkFileStatus.UPLOADED, BulkFileStatus.PARSING));
        assertTrue(BulkFileStateMachine.allows(BulkFileStatus.PARSING, BulkFileStatus.PARSED));
        assertTrue(BulkFileStateMachine.allows(BulkFileStatus.PARSING, BulkFileStatus.PARSED_WITH_ERRORS));
        assertTrue(BulkFileStateMachine.allows(BulkFileStatus.PARSING, BulkFileStatus.REJECTED));
        assertTrue(BulkFileStateMachine.allows(BulkFileStatus.PARSED, BulkFileStatus.LINES_RELEASED));
        assertTrue(BulkFileStateMachine.allows(BulkFileStatus.PARSED_WITH_ERRORS, BulkFileStatus.LINES_RELEASED));
        assertFalse(BulkFileStateMachine.allows(BulkFileStatus.REJECTED, BulkFileStatus.PARSED));
        assertFalse(BulkFileStateMachine.allows(BulkFileStatus.UPLOADED, BulkFileStatus.PARSED));
    }

    @Test
    void bulkLineEdges() {
        assertTrue(BulkLineStateMachine.allows(BulkLineStatus.VALID, BulkLineStatus.APPROVED));
        assertTrue(BulkLineStateMachine.allows(BulkLineStatus.APPROVED, BulkLineStatus.RELEASED));
        assertTrue(BulkLineStateMachine.allows(BulkLineStatus.VALID, BulkLineStatus.REJECTED));
        assertFalse(BulkLineStateMachine.allows(BulkLineStatus.INVALID, BulkLineStatus.VALID));
        assertFalse(BulkLineStateMachine.allows(BulkLineStatus.INVALID, BulkLineStatus.APPROVED));
    }

    @Test
    void beneficiaryEdges() {
        assertTrue(BeneficiaryStateMachine.allows(BeneficiaryStatus.PENDING_COOLING, BeneficiaryStatus.ACTIVE));
        assertTrue(BeneficiaryStateMachine.allows(BeneficiaryStatus.ACTIVE, BeneficiaryStatus.SUSPENDED));
        assertTrue(BeneficiaryStateMachine.allows(BeneficiaryStatus.ACTIVE, BeneficiaryStatus.PENDING_COOLING));
        assertTrue(BeneficiaryStateMachine.allows(BeneficiaryStatus.SUSPENDED, BeneficiaryStatus.ACTIVE));
        assertFalse(BeneficiaryStateMachine.allows(BeneficiaryStatus.SUSPENDED, BeneficiaryStatus.PENDING_COOLING));
        assertFalse(BeneficiaryStateMachine.allows(BeneficiaryStatus.PENDING_COOLING, BeneficiaryStatus.SUSPENDED));
    }

    @Test
    void reversalEdges() {
        assertTrue(ReversalStateMachine.allows(ReversalStatus.REQUESTED, ReversalStatus.APPROVED));
        assertTrue(ReversalStateMachine.allows(ReversalStatus.REQUESTED, ReversalStatus.DECLINED));
        assertTrue(ReversalStateMachine.allows(ReversalStatus.APPROVED, ReversalStatus.POSTED));
        assertFalse(ReversalStateMachine.allows(ReversalStatus.REQUESTED, ReversalStatus.POSTED));
        assertFalse(ReversalStateMachine.allows(ReversalStatus.DECLINED, ReversalStatus.APPROVED));
    }
}
