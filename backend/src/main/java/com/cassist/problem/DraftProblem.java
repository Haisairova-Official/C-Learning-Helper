package com.cassist.problem;

import java.math.BigDecimal;
import java.util.List;

/** Drafts can omit content that is required later at publication. */
public record DraftProblem(String candidateId, String title, String statement, String inputDescription,
        String outputDescription, List<Sample> samples, String referenceAnswer, List<PrivateTestCase> testCases,
        Long timeLimitMs, Long memoryLimitMb, Long codeLimitBytes, BigDecimal maxScore,
        String problemType, String language, String functionSignature, String starterCode, String judgeTemplate) {
    public DraftProblem {
        samples = samples == null ? List.of() : List.copyOf(samples);
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        problemType = problemType == null ? "PROGRAM" : problemType;
        language = language == null ? "C17" : language;
        functionSignature = functionSignature == null ? "" : functionSignature;
        starterCode = starterCode == null ? "" : starterCode;
        judgeTemplate = judgeTemplate == null ? "" : judgeTemplate;
    }
    public DraftProblem(String candidateId, String title, String statement, String inputDescription,
            String outputDescription, List<Sample> samples, String referenceAnswer, List<PrivateTestCase> testCases,
            Long timeLimitMs, Long memoryLimitMb, Long codeLimitBytes, BigDecimal maxScore) {
        this(candidateId,title,statement,inputDescription,outputDescription,samples,referenceAnswer,testCases,
                timeLimitMs,memoryLimitMb,codeLimitBytes,maxScore,"PROGRAM","C17","","","");
    }
}
