package com.cassist.problem;

import java.util.List;

/** Authorized server-internal version snapshot; not an HTTP response DTO. */
public record ProblemSnapshot(StudentProblemView problem, String referenceAnswer, List<PrivateTestCase> testCases,
        String judgeTemplate) {
    public ProblemSnapshot { testCases = List.copyOf(testCases); }
    public ProblemSnapshot(StudentProblemView problem, String referenceAnswer, List<PrivateTestCase> testCases) {
        this(problem, referenceAnswer, testCases, "");
    }
    @Override public String toString() { return "ProblemSnapshot[versionId=" + problem.versionId() + "]"; }
}
