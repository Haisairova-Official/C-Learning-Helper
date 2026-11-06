package com.cassist.judge.submission;

import com.cassist.judge.core.JudgeModels.CaseResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SubmissionModels {
    private SubmissionModels() {}
    public record CreateRequest(@NotBlank String assignmentId, @NotBlank String problemId,
            @NotBlank String problemVersionId, @NotBlank @Size(max=65536) String sourceCode,
            @NotBlank String language) {}
    public record Accepted(String id, String status, Instant submittedAt) {}
    public record View(String id, String studentId, String assignmentId, String problemId,
            String problemVersionId, String language, String sourceCode, String status, String verdict,
            BigDecimal score, BigDecimal maxScore, String compileMessage, List<CaseView> caseResults,
            Instant submittedAt, Instant finishedAt) {}
    public record CaseView(int caseIndex, String status, BigDecimal score, long timeMs, long memoryKb) {
        public static CaseView of(CaseResult c) { return new CaseView(c.caseIndex(),c.status().name(),c.score(),c.timeMs(),c.memoryKb()); }
    }
    public record Row(String id,String studentId,String assignmentId,String problemId,String versionId,
            String sourceCode,String language,String status,String verdict,BigDecimal score,BigDecimal maxScore,
            String compileMessage,String requestHash,Instant submittedAt,Instant finishedAt) {}
    public record Task(String id,String submissionId,String token,int attempt) {}
}
