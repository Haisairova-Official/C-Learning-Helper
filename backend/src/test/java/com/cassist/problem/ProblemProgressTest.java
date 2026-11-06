package com.cassist.problem;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProblemProgressTest {
    @Test void distinguishesUnsubmittedPendingAndInfrastructureFailure() {
        assertEquals(ProblemProgress.empty(), ProblemProgress.summarize(0, 0, 0, 0, 0, null));
        var pending=ProblemProgress.summarize(1, 0, 0, 0, 1, null);
        assertEquals("PENDING",pending.status()); assertTrue(pending.pending()); assertNull(pending.bestScore());
        var failed=ProblemProgress.summarize(1, 0, 0, 0, 0, null);
        assertEquals("SYSTEM_ERROR",failed.status()); assertFalse(failed.pending()); assertNull(failed.bestScore());
    }
    @Test void zeroMarkFinishedJudgementIsWrongAndPositiveMarkIsPartial() {
        assertEquals("WRONG",ProblemProgress.summarize(1, 1, 0, 0, 0, BigDecimal.ZERO).status());
        assertEquals("PARTIAL",ProblemProgress.summarize(2, 2, 0, 1, 0, new BigDecimal("50")).status());
    }
    @Test void laterFailureOrPendingSubmissionDoesNotDowngradeAccepted() {
        var best=ProblemProgress.summarize(4, 3, 1, 1, 1, new BigDecimal("100"));
        assertEquals("ACCEPTED",best.status()); assertTrue(best.pending());
        assertEquals(new BigDecimal("100"),best.bestScore());
    }
    @Test void fullScoreAloneDoesNotClaimAllCasesPassed() {
        // A zero-point test can still fail, so green requires an ACCEPTED verdict.
        assertEquals("PARTIAL",ProblemProgress.summarize(1, 1, 0, 1, 0, new BigDecimal("100")).status());
    }
}
