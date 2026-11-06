package com.cassist.judge.core;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class JudgeModels {
    private JudgeModels() {}

    public record Limits(long cpuTimeMs, long wallTimeMs, long memoryBytes,
                         long stackBytes, int processLimit, int outputBytes) {
        public Limits {
            if (cpuTimeMs <= 0 || cpuTimeMs > 60_000 || wallTimeMs < cpuTimeMs || wallTimeMs > 120_000
                    || memoryBytes < 1_048_576 || memoryBytes > 1_073_741_824L
                    || stackBytes <= 0 || stackBytes > memoryBytes
                    || processLimit < 1 || processLimit > 32 || outputBytes < 1 || outputBytes > 1_048_576) {
                throw new IllegalArgumentException("Resource limits outside the supported policy");
            }
        }
    }

    public record TestCase(String id, int index, String input, String expectedOutput,
                           BigDecimal score, ComparisonMode comparisonMode) {
        public TestCase {
            Objects.requireNonNull(id); Objects.requireNonNull(input); Objects.requireNonNull(expectedOutput);
            Objects.requireNonNull(score); Objects.requireNonNull(comparisonMode);
            if (id.isBlank() || index < 1 || score.signum() < 0) throw new IllegalArgumentException("Invalid test case");
        }
        @Override public String toString() { return "TestCase[id=" + id + ", index=" + index + "]"; }
    }

    public record ProblemSnapshot(String versionId, BigDecimal maxScore, Limits limits, List<TestCase> cases) {
        public ProblemSnapshot {
            Objects.requireNonNull(versionId); Objects.requireNonNull(maxScore); Objects.requireNonNull(limits);
            cases = List.copyOf(cases);
            if (versionId.isBlank() || maxScore.signum() <= 0 || cases.isEmpty() || cases.size() > 100) {
                throw new IllegalArgumentException("Invalid problem snapshot");
            }
            var ids = new HashSet<String>();
            var indexes = new HashSet<Integer>();
            BigDecimal sum = BigDecimal.ZERO;
            for (TestCase test : cases) {
                if (!ids.add(test.id()) || !indexes.add(test.index())) throw new IllegalArgumentException("Duplicate case");
                sum = sum.add(test.score());
            }
            if (sum.compareTo(maxScore) != 0) throw new IllegalArgumentException("Case scores must equal maxScore");
        }
    }

    /** Deliberately excludes test input, expected output and program stdout/stderr. */
    public record CaseResult(int caseIndex, Verdict status, BigDecimal score, long timeMs, long memoryKb,
                             long cpuTimeNs, long wallTimeNs, long memoryBytes) {}

    public record Outcome(Verdict verdict, BigDecimal score, BigDecimal maxScore,
                          String compileMessage, List<CaseResult> caseResults, String errorCode) {
        public Outcome { caseResults = List.copyOf(caseResults); }
        public boolean systemError() { return verdict == Verdict.SYSTEM_ERROR; }
    }
}
