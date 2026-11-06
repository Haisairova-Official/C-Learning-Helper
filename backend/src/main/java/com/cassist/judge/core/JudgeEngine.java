package com.cassist.judge.core;

import com.cassist.judge.core.JudgeModels.*;
import com.cassist.judge.protocol.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Runs one immutable submission. Persistence, authorization and retry leases belong to the worker layer. */
public final class JudgeEngine {
    private final GoJudgeTransport transport;
    public JudgeEngine(GoJudgeTransport transport) { this.transport = Objects.requireNonNull(transport); }

    public Outcome judge(String source, ProblemSnapshot problem) {
        return judge(source, problem, () -> {});
    }
    public Outcome judge(String source, ProblemSnapshot problem, Runnable onRunning) {
        return judge(CompilationSource.program(source, CompileLanguage.C17), problem, onRunning);
    }
    public Outcome judge(CompilationSource source, ProblemSnapshot problem) {
        return judge(source, problem, () -> {});
    }
    public Outcome judge(CompilationSource source, ProblemSnapshot problem, Runnable onRunning) {
        Objects.requireNonNull(problem); Objects.requireNonNull(onRunning);
        var compileRequest = GoJudgeRequests.compile(source);
        var cachedIds = new LinkedHashSet<String>();
        var results = new ArrayList<CaseResult>();
        Outcome outcome;
        try {
            GoJudgeResult compilation = transport.execute(compileRequest);
            cachedIds.addAll(compilation.fileIds().values());
            Verdict compilationVerdict = compileVerdict(compilation);
            if (compilationVerdict == Verdict.SYSTEM_ERROR) {
                outcome = error(problem, results, "JUDGE_COMPILE_INFRASTRUCTURE_ERROR");
            } else if (compilationVerdict == Verdict.COMPILATION_ERROR) {
                outcome = new Outcome(Verdict.COMPILATION_ERROR, BigDecimal.ZERO, problem.maxScore(),
                        safeCompileDiagnostic(compilation.files().getOrDefault("stderr", ""), source), List.of(), null);
            } else {
                String binaryId = compilation.fileIds().get("main");
                if (binaryId == null) throw new JudgeTransportException("JUDGE_MISSING_BINARY");
                GoJudgeRequests.requireFileId(binaryId);
                onRunning.run();
                for (TestCase test : problem.cases().stream().sorted(Comparator.comparingInt(TestCase::index)).toList()) {
                    GoJudgeResult execution = transport.execute(GoJudgeRequests.run(binaryId, test.input(), problem.limits()));
                    cachedIds.addAll(execution.fileIds().values());
                    Verdict verdict = runtimeVerdict(execution);
                    if (verdict == Verdict.ACCEPTED) {
                        if (!execution.files().containsKey("stdout")) throw new JudgeTransportException("JUDGE_MISSING_OUTPUT");
                        verdict = OutputComparator.compare(test.expectedOutput(), execution.files().get("stdout"), test.comparisonMode());
                    }
                    if (verdict == Verdict.SYSTEM_ERROR) throw new JudgeTransportException("JUDGE_RUNTIME_INFRASTRUCTURE_ERROR");
                    results.add(new CaseResult(test.index(), verdict, verdict == Verdict.ACCEPTED ? test.score() : BigDecimal.ZERO,
                            ceilUnit(execution.time(), 1_000_000), ceilUnit(execution.memory(), 1024),
                            execution.time(), execution.runTime(), execution.memory()));
                }
                BigDecimal score = results.stream().map(CaseResult::score).reduce(BigDecimal.ZERO, BigDecimal::add);
                // Zero-score diagnostic cases must also pass for a fully accepted result.
                Verdict summary = results.stream().allMatch(c -> c.status() == Verdict.ACCEPTED) ? Verdict.ACCEPTED
                        : score.signum() > 0 ? Verdict.PARTIAL_ACCEPTED : results.stream()
                            .map(CaseResult::status).filter(v -> v != Verdict.ACCEPTED && v != Verdict.PRESENTATION_ERROR)
                            .findFirst().orElse(Verdict.PRESENTATION_ERROR);
                outcome = new Outcome(summary, score, problem.maxScore(), "", results, null);
            }
        } catch (JudgeTransportException e) {
            outcome = error(problem, results, e.code());
        } catch (RuntimeException e) {
            outcome = error(problem, results, "JUDGE_INVALID_PROTOCOL_OR_SNAPSHOT");
        }
        // Clear every returned cache id, including on compilation/runtime/protocol errors.
        boolean cleanupFailed = false;
        for (String id : cachedIds) {
            try { GoJudgeRequests.requireFileId(id); transport.deleteCachedFile(id); }
            catch (JudgeTransportException | IllegalArgumentException e) { cleanupFailed = true; }
        }
        return cleanupFailed && !outcome.systemError() ? error(problem, results, "JUDGE_CACHE_CLEANUP_FAILED") : outcome;
    }

    private static Outcome error(ProblemSnapshot p, List<CaseResult> results, String code) {
        return new Outcome(Verdict.SYSTEM_ERROR, null, p.maxScore(), "", results, code);
    }

    static Verdict compileVerdict(GoJudgeResult r) {
        if ("Accepted".equals(r.status())) return validSuccess(r) ? Verdict.ACCEPTED : Verdict.SYSTEM_ERROR;
        return switch (r.status() == null ? "" : r.status()) {
            case "Nonzero Exit Status", "Signalled", "Time Limit Exceeded", "Memory Limit Exceeded", "Output Limit Exceeded" -> Verdict.COMPILATION_ERROR;
            default -> Verdict.SYSTEM_ERROR;
        };
    }

    static Verdict runtimeVerdict(GoJudgeResult r) {
        if (r.time() == null || r.time() < 0 || r.memory() == null || r.memory() < 0 || r.runTime() == null || r.runTime() < 0) return Verdict.SYSTEM_ERROR;
        return switch (r.status() == null ? "" : r.status()) {
            case "Accepted" -> validSuccess(r) ? Verdict.ACCEPTED : Verdict.SYSTEM_ERROR;
            case "Nonzero Exit Status", "Signalled", "Dangerous Syscall" -> Verdict.RUNTIME_ERROR;
            case "Time Limit Exceeded" -> Verdict.TIME_LIMIT_EXCEEDED;
            case "Memory Limit Exceeded" -> Verdict.MEMORY_LIMIT_EXCEEDED;
            case "Output Limit Exceeded" -> Verdict.OUTPUT_LIMIT_EXCEEDED;
            default -> Verdict.SYSTEM_ERROR;
        };
    }

    private static boolean validSuccess(GoJudgeResult r) {
        return r.exitStatus() != null && r.exitStatus() == 0 && r.time() != null && r.time() >= 0
                && r.memory() != null && r.memory() >= 0;
    }
    private static long ceilUnit(Long amount, long divisor) {
        if (amount == null || amount < 0) throw new IllegalArgumentException("Invalid usage metric");
        return amount / divisor + (amount % divisor == 0 ? 0 : 1);
    }
    private static String safeCompileDiagnostic(String message, CompilationSource source) {
        if (source.hasPrivateTemplate()) {
            // Compiler messages can quote private declarations even at a student location. Only
            // numeric locations and our own text cross the boundary, never compiler prose/tokens.
            var location = Pattern.compile("^" + Pattern.quote(source.language().studentFile())
                    + ":([0-9]{1,8})(?::([0-9]{1,8}))?: (?:fatal )?error:");
            String locations = message.lines().map(location::matcher).filter(m -> m.find())
                    .map(m -> source.language().studentFile() + ":" + m.group(1)
                            + (m.group(2) == null ? "" : ":" + m.group(2))
                            + ": 编译错误，请检查该位置的语法、类型和函数接口。")
                    .distinct().limit(10).reduce((a,b) -> a + "\n" + b).orElse("");
            return locations.isEmpty() ? "函数编译失败，请检查函数签名、实现及资源限制；如仍有问题，请联系教师检查评测配置。" : locations;
        }
        // Preserve only diagnostics tied to the student's fixed source name; discard other server/tool paths.
        String filtered = message.lines().filter(line -> line.startsWith(source.language().sourceFile() + ":")).map(line ->
                line.replaceAll("/[^\\s'\"<>]+", "[path]").replaceAll("[\\p{Cntrl}&&[^\\t]]", ""))
                .reduce((left, right) -> left + "\n" + right).orElse("编译失败，请检查语法或编译资源限制。");
        return filtered.substring(0, Math.min(4096, filtered.length()));
    }
}
