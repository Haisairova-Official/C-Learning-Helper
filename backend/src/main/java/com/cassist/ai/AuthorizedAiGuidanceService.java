package com.cassist.ai;

import java.util.Objects;

import com.cassist.ai.AiProvider.ProblemContext;
import com.cassist.ai.AiProvider.ProviderException;
import com.cassist.identity.Actor;
import com.cassist.identity.AiReplyStyle;
import com.cassist.problem.ProblemAccessPort;
import com.cassist.problem.ProblemSnapshot;
import com.cassist.problem.Sample;

/** Adapter for A's authorization port. Actor must come from CurrentActor, never from JSON. */
public final class AuthorizedAiGuidanceService {
    private final ProblemAccessPort problems;
    private final AiGuidanceCore core;

    public AuthorizedAiGuidanceService(ProblemAccessPort problems, AiGuidanceCore core) {
        this.problems = Objects.requireNonNull(problems);
        this.core = Objects.requireNonNull(core);
    }

    public AiGuidanceCore.Result analyze(Actor actor, String assignmentId, String problemId,
            String problemVersionId, String currentCode) throws ProviderException {
        return analyze(actor, assignmentId, problemId, problemVersionId, currentCode, AiReplyStyle.GUIDED);
    }
    public AiGuidanceCore.Result analyze(Actor actor, String assignmentId, String problemId,
            String problemVersionId, String currentCode, AiReplyStyle style) throws ProviderException {
        return analyze(actor, assignmentId, problemId, problemVersionId, currentCode, style, "");
    }
    public AiGuidanceCore.Result analyze(Actor actor, String assignmentId, String problemId,
            String problemVersionId, String currentCode, AiReplyStyle style, String addressName) throws ProviderException {
        ProblemSnapshot snapshot = Objects.requireNonNull(problems.requireForStudent(
                actor, assignmentId, problemId, problemVersionId), "Authorization port returned no problem");
        if (!problemId.equals(snapshot.problem().id()) || !problemVersionId.equals(snapshot.problem().versionId()))
            throw new IllegalStateException("Authorized problem version mismatch");
        CodeSnapshot.capture(currentCode, Math.toIntExact(Math.min(Integer.MAX_VALUE, snapshot.problem().codeLimitBytes())));
        StringBuilder statement = new StringBuilder(snapshot.problem().title()).append('\n')
                .append(snapshot.problem().statement()).append("\n输入格式：\n")
                .append(snapshot.problem().inputDescription()).append("\n输出格式：\n")
                .append(snapshot.problem().outputDescription());
        statement.append("\n语言：").append(snapshot.problem().language());
        if ("FUNCTION".equals(snapshot.problem().problemType())) {
            statement.append("\n题型：函数补全。学生只提交函数实现；主程序由评测系统提供，不应要求学生补写 main。\n公开函数接口：\n")
                    .append(snapshot.problem().functionSignature());
        } else statement.append("\n题型：完整程序。");
        for (Sample sample : snapshot.problem().samples()) {
            statement.append("\n公开输入样例：\n").append(sample.input())
                    .append("\n公开输出样例：\n").append(sample.output());
        }
        ProblemContext context = new ProblemContext(assignmentId, problemId, problemVersionId,
                statement.toString(), snapshot.referenceAnswer());
        return core.analyzeAuthorized(context, currentCode, style, addressName);
    }
}
