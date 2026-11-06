package com.cassist.importer;

import java.math.BigDecimal;
import java.util.List;

/** Teacher-only parsing output. Never serialize this type through student APIs. */
public record ImportPreview(String parserVersion, List<Candidate> candidates, List<Issue> issues) {
    public ImportPreview {
        candidates = List.copyOf(candidates);
        issues = List.copyOf(issues);
    }

    public record Candidate(String candidateId, String title, String statement,
            String inputDescription, String outputDescription, List<Sample> samples,
            String referenceAnswer, String privateNotes, String unresolvedContent,
            List<TestCase> testCases, BigDecimal maxScore, long timeLimitMs,
            long memoryLimitMb, SourceLocation sourceLocation, List<Issue> issues,
            boolean needsReview, String problemType, String language, String functionSignature,
            String starterCode, String judgeTemplate, Long codeLimitBytes) {
        public Candidate {
            samples = samples == null ? List.of() : List.copyOf(samples);
            testCases = testCases == null ? List.of() : List.copyOf(testCases);
            issues = issues == null ? List.of() : List.copyOf(issues);
            problemType = problemType == null ? "PROGRAM" : problemType;
            language = language == null ? "C17" : language;
            functionSignature = functionSignature == null ? "" : functionSignature;
            starterCode = starterCode == null ? "" : starterCode;
            judgeTemplate = judgeTemplate == null ? "" : judgeTemplate;
            codeLimitBytes = codeLimitBytes == null ? 65536L : codeLimitBytes;
        }
        public Candidate(String candidateId, String title, String statement,
                String inputDescription, String outputDescription, List<Sample> samples,
                String referenceAnswer, String privateNotes, String unresolvedContent,
                List<TestCase> testCases, BigDecimal maxScore, long timeLimitMs,
                long memoryLimitMb, SourceLocation sourceLocation, List<Issue> issues,
                boolean needsReview) {
            this(candidateId, title, statement, inputDescription, outputDescription, samples,
                    referenceAnswer, privateNotes, unresolvedContent, testCases, maxScore,
                    timeLimitMs, memoryLimitMb, sourceLocation, issues, needsReview,
                    "PROGRAM", "C17", "", "", "", 65536L);
        }
        public Candidate(String candidateId, String title, String statement,
                String inputDescription, String outputDescription, List<Sample> samples,
                String referenceAnswer, String privateNotes, String unresolvedContent,
                List<TestCase> testCases, BigDecimal maxScore, long timeLimitMs,
                long memoryLimitMb, SourceLocation sourceLocation, List<Issue> issues,
                boolean needsReview, String problemType, String language, String functionSignature,
                String starterCode, String judgeTemplate) {
            this(candidateId, title, statement, inputDescription, outputDescription, samples,
                    referenceAnswer, privateNotes, unresolvedContent, testCases, maxScore,
                    timeLimitMs, memoryLimitMb, sourceLocation, issues, needsReview,
                    problemType, language, functionSignature, starterCode, judgeTemplate, 65536L);
        }
    }

    public record Sample(String key, String input, String output, String explanation) {
        public Sample(String key, String input, String output) { this(key, input, output, null); }
    }
    public record TestCase(String key, String input, String expectedOutput,
            String visibility, BigDecimal score, String comparisonMode) { }
    public record SourceLocation(int startLine, int endLine) {
        public SourceLocation {
            if (startLine < 1 || endLine < startLine) throw new IllegalArgumentException("Invalid source location");
        }
    }
    public enum Severity { INFO, WARNING, ERROR }
    public record Issue(String code, Severity severity, String field,
            SourceLocation sourceLocation, String message) { }
}
