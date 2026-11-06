package com.cassist.problem;

import java.math.BigDecimal;

/** Personal progress for one assignment version; only finished judgements affect the colour. */
public record ProblemProgress(String status, BigDecimal bestScore, boolean pending) {
    public static ProblemProgress empty() { return new ProblemProgress("UNSUBMITTED", null, false); }

    public static ProblemProgress summarize(int submissions, int judged, int accepted, int partial,
            int pending, BigDecimal bestScore) {
        String status;
        if (accepted > 0) status = "ACCEPTED";
        else if (partial > 0 || (judged > 0 && bestScore != null && bestScore.signum() > 0)) status = "PARTIAL";
        else if (judged > 0) status = "WRONG";
        else if (pending > 0) status = "PENDING";
        else status = submissions > 0 ? "SYSTEM_ERROR" : "UNSUBMITTED";
        return new ProblemProgress(status, judged > 0 ? bestScore : null, pending > 0);
    }
}
