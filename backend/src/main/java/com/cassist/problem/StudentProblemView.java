package com.cassist.problem;

import java.math.BigDecimal;
import java.util.List;

/** Only this public view belongs in student problem responses. */
public record StudentProblemView(String id, String versionId, String title, String statement,
        String inputDescription, String outputDescription, List<Sample> samples,
        long timeLimitMs, long memoryLimitMb, long codeLimitBytes, BigDecimal maxScore,
        String problemType, String language, String functionSignature, String starterCode) {
    public StudentProblemView {
        samples = List.copyOf(samples);
        problemType = problemType == null ? "PROGRAM" : problemType;
        language = language == null ? "C17" : language;
        functionSignature = functionSignature == null ? "" : functionSignature;
        starterCode = starterCode == null ? "" : starterCode;
    }
    public StudentProblemView(String id, String versionId, String title, String statement,
            String inputDescription, String outputDescription, List<Sample> samples,
            long timeLimitMs, long memoryLimitMb, long codeLimitBytes, BigDecimal maxScore) {
        this(id, versionId, title, statement, inputDescription, outputDescription, samples,
                timeLimitMs, memoryLimitMb, codeLimitBytes, maxScore, "PROGRAM", "C17", "", "");
    }
}
