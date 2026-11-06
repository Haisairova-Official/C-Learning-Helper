package com.cassist.judge.core;

import com.cassist.judge.core.JudgeModels.*;
import com.cassist.judge.protocol.GoJudgeRequests;
import com.cassist.problem.ProblemSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

/** Converts an already authorized A-module snapshot; it does not grant access itself. */
public final class JudgeProblemMapper {
    private JudgeProblemMapper() {}

    public static JudgeModels.ProblemSnapshot fromAuthorized(ProblemSnapshot snapshot, String sourceCode) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(sourceCode);
        var p = snapshot.problem();
        if (p.codeLimitBytes() < 1 || p.codeLimitBytes() > GoJudgeRequests.MAX_SOURCE_BYTES
                || sourceCode.getBytes(StandardCharsets.UTF_8).length > p.codeLimitBytes()) {
            throw new IllegalArgumentException("Source exceeds problem limit or invalid code limit");
        }
        if (p.timeLimitMs() < 1 || p.timeLimitMs() > 60_000) throw new IllegalArgumentException("Invalid time limit");
        long memory = Math.multiplyExact(p.memoryLimitMb(), 1_048_576L);
        long wallTime = Math.min(120_000, Math.max(p.timeLimitMs() * 3, p.timeLimitMs() + 500));
        var limits = new Limits(p.timeLimitMs(), wallTime, memory, Math.min(memory, 8_388_608L), 1, 65_536);
        var tests = new ArrayList<TestCase>();
        int index = 1;
        for (var t : snapshot.testCases()) {
            tests.add(new TestCase(t.id(), index++, t.input(), t.expectedOutput(), t.score(),
                    ComparisonMode.valueOf(t.comparisonMode().name())));
        }
        return new JudgeModels.ProblemSnapshot(p.versionId(), p.maxScore(), limits, tests);
    }
}
