package com.cassist.ai;

import java.util.List;
import java.util.Objects;

/** Vendor-neutral boundary. Mapping to the school's actual HTTP protocol is intentionally not assumed. */
public interface AiProvider {
    ProviderMode mode();
    Guidance analyze(AnalysisRequest request) throws ProviderException;

    enum ProviderMode { MOCK, SCHOOL, DEEPSEEK }
    enum Failure { UNAVAILABLE, TIMEOUT, INVALID_RESPONSE, UNSAFE_RESPONSE, AUTHENTICATION, QUOTA_EXCEEDED, RATE_LIMITED, INTERRUPTED }

    record ProblemContext(String assignmentId, String problemId, String problemVersionId,
            String statement, String referenceAnswer) {
        public ProblemContext {
            Objects.requireNonNull(assignmentId);
            Objects.requireNonNull(problemId);
            Objects.requireNonNull(problemVersionId);
            Objects.requireNonNull(statement);
            Objects.requireNonNull(referenceAnswer);
        }
        @Override public String toString() { return "ProblemContext[problemId=" + problemId + ", problemVersionId=" + problemVersionId + "]"; }
    }

    /** Keep high-priority instruction separate from all untrusted data in the vendor adapter. */
    record AnalysisRequest(String systemInstruction, String promptVersion, ProblemContext problem, CodeSnapshot code, String addressName) {
        public AnalysisRequest(String systemInstruction, String promptVersion, ProblemContext problem, CodeSnapshot code) {
            this(systemInstruction, promptVersion, problem, code, "");
        }
        public AnalysisRequest {
            Objects.requireNonNull(systemInstruction);
            Objects.requireNonNull(promptVersion);
            Objects.requireNonNull(problem);
            Objects.requireNonNull(code);
            Objects.requireNonNull(addressName);
        }
        @Override public String toString() { return "AnalysisRequest[promptVersion=" + promptVersion + ", problem=" + problem + ", code=" + code + "]"; }
    }

    record Finding(Integer startLine, Integer endLine, String category, String reason, String hint) { }
    record Guidance(String summary, List<Finding> findings) { }

    final class ProviderException extends Exception {
        private final Failure failure;
        public ProviderException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
        public Failure failure() { return failure; }
    }
}
