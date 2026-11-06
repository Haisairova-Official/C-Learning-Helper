package com.cassist.problem;

import java.math.BigDecimal;
import java.util.List;

public record TeacherProblemView(String id, String versionId, String title, String statement,
        String inputDescription, String outputDescription, List<Sample> samples, Long timeLimitMs,
        Long memoryLimitMb, Long codeLimitBytes, BigDecimal maxScore, String referenceAnswer,
        List<PrivateTestCase> testCases, String state, String problemType, String language,
        String functionSignature, String starterCode, String judgeTemplate) {
    public TeacherProblemView {
        samples = samples == null ? List.of() : List.copyOf(samples);
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        problemType = problemType == null ? "PROGRAM" : problemType;
        language = language == null ? "C17" : language;
        functionSignature = functionSignature == null ? "" : functionSignature;
        starterCode = starterCode == null ? "" : starterCode;
        judgeTemplate = judgeTemplate == null ? "" : judgeTemplate;
    }
    public TeacherProblemView(String id, String versionId, String title, String statement,
            String inputDescription, String outputDescription, List<Sample> samples, Long timeLimitMs,
            Long memoryLimitMb, Long codeLimitBytes, BigDecimal maxScore, String referenceAnswer,
            List<PrivateTestCase> testCases, String state) {
        this(id, versionId, title, statement, inputDescription, outputDescription, samples, timeLimitMs,
                memoryLimitMb, codeLimitBytes, maxScore, referenceAnswer, testCases, state,
                "PROGRAM", "C17", "", "", "");
    }
}
