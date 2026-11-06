package com.cassist.judge;

import com.cassist.judge.core.*;
import com.cassist.judge.core.JudgeModels.*;
import com.cassist.judge.protocol.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JudgeCoreTest {
    static final Limits LIMITS = new Limits(1000, 3000, 67_108_864, 8_388_608, 1, 65_536);
    static final String SOURCE = "int main(void) { return 0; }";
    static ProblemSnapshot problem() {
        return new ProblemSnapshot("v1", new BigDecimal("10"), LIMITS, List.of(
                new TestCase("t1", 1, "private input 1", "2\n", new BigDecimal("3.5"), ComparisonMode.EXACT),
                new TestCase("t2", 2, "private input 2", "4", new BigDecimal("6.5"), ComparisonMode.TOKENS)));
    }
    static GoJudgeResult result(String status, int exit, String stdout) {
        return new GoJudgeResult(status, exit, 1_000_001L, 1025L, 2_000_000L, Map.of("stdout", stdout, "stderr", "private stderr"), Map.of());
    }
    static GoJudgeResult compiled() {
        return new GoJudgeResult("Accepted", 0, 12L, 4096L, 30L, Map.of("stdout", "", "stderr", ""), Map.of("main", "binary_1"));
    }
    static class Fake implements GoJudgeTransport {
        final Queue<GoJudgeResult> answers = new ArrayDeque<>();
        final List<String> deleted = new ArrayList<>();
        final List<Map<String,Object>> requests = new ArrayList<>();
        boolean failCleanup;
        Fake(GoJudgeResult... responses) { answers.addAll(Arrays.asList(responses)); }
        public GoJudgeResult execute(Map<String,Object> req) throws JudgeTransportException {
            requests.add(req);
            if (answers.isEmpty()) throw new JudgeTransportException("JUDGE_TRANSPORT_FAILURE");
            return answers.remove();
        }
        public void deleteCachedFile(String id) throws JudgeTransportException {
            deleted.add(id);
            if (failCleanup) throw new JudgeTransportException("JUDGE_CLEANUP_HTTP_ERROR");
        }
    }

    @Test void exactAllowsOneFinalNewlineAndNormalizesCrLf() {
        assertTrue(OutputComparator.matches("a\r\nb\n", "a\nb\n", ComparisonMode.EXACT));
        assertTrue(OutputComparator.matches("a\n", "a", ComparisonMode.EXACT));
        assertTrue(OutputComparator.matches("a", "a\n", ComparisonMode.EXACT));
        assertTrue(OutputComparator.matches("a\r\n", "a", ComparisonMode.EXACT));
        assertTrue(OutputComparator.matches("a\r\nb\n", "a\nb", ComparisonMode.EXACT));
        assertFalse(OutputComparator.matches("a \n", "a\n", ComparisonMode.EXACT));
        assertFalse(OutputComparator.matches("a\r", "a\n", ComparisonMode.EXACT));
        assertFalse(OutputComparator.matches("a\n", "a\n\n", ComparisonMode.EXACT));
        assertFalse(OutputComparator.matches("a\n\n", "a", ComparisonMode.EXACT));
    }
    @Test void distinguishesFormattingFromContentWithoutNumericCoercion() {
        for (String actual : List.of("1  2\n", " 1 2\n", "1 2 \n", "1\t2", "1\n2", "1 2\n\n"))
            assertEquals(Verdict.PRESENTATION_ERROR, OutputComparator.compare("1 2\n",actual,ComparisonMode.EXACT),actual);
        for (String actual : List.of("12", "1 3", "1.0 2", "1 2 3", "result: 1 2", "1\u00a02"))
            assertEquals(Verdict.WRONG_ANSWER, OutputComparator.compare("1 2\n",actual,ComparisonMode.EXACT),actual);
        assertEquals(Verdict.ACCEPTED,OutputComparator.compare("1 2\n","1 2",ComparisonMode.EXACT));
        assertEquals(Verdict.ACCEPTED,OutputComparator.compare("1 2\n"," 1\t2\n\n",ComparisonMode.TOKENS));
    }
    @Test void tokensAreTextAndAsciiWhitespaceOnly() {
        assertTrue(OutputComparator.matches("  1\t2\n", "1 2", ComparisonMode.TOKENS));
        assertTrue(OutputComparator.matches("", " \r\n", ComparisonMode.TOKENS));
        assertFalse(OutputComparator.matches("1", "1.0", ComparisonMode.TOKENS));
        assertFalse(OutputComparator.matches("1 2", "1 2 3", ComparisonMode.TOKENS));
        assertFalse(OutputComparator.matches("1 2", "1\u00a02", ComparisonMode.TOKENS));
    }
    @Test void acceptedProcessStillRequiresOutputComparison() {
        Fake f = new Fake(compiled(), result("Accepted",0,"wrong"), result("Accepted",0,"4\n"));
        Outcome outcome = new JudgeEngine(f).judge(SOURCE, problem());
        assertEquals(Verdict.PARTIAL_ACCEPTED, outcome.verdict());
        assertEquals(new BigDecimal("6.5"), outcome.score());
        assertEquals(Verdict.WRONG_ANSWER, outcome.caseResults().getFirst().status());
        assertEquals(2, outcome.caseResults().getFirst().timeMs());
        assertEquals(2, outcome.caseResults().getFirst().memoryKb());
        assertEquals(List.of("binary_1"), f.deleted);
        assertFalse(outcome.toString().contains("private"));
    }
    @Test void allPassingScoresExactlyAndDeletesBinary() {
        Fake f = new Fake(compiled(), result("Accepted",0,"2\n"), result("Accepted",0,"4"));
        Outcome outcome = new JudgeEngine(f).judge(SOURCE, problem());
        assertEquals(Verdict.ACCEPTED, outcome.verdict());
        assertEquals(0, new BigDecimal("10").compareTo(outcome.score()));
        assertEquals(List.of("binary_1"), f.deleted);
    }
    @Test void formatOnlyResultGetsNoPointsAndDoesNotLeakOutput() {
        var exact = new ProblemSnapshot("v",BigDecimal.TEN,LIMITS,List.of(
                new TestCase("t",1,"private input","secret expected",BigDecimal.TEN,ComparisonMode.EXACT)));
        var outcome=new JudgeEngine(new Fake(compiled(),result("Accepted",0," secret  expected\n"))).judge(SOURCE,exact);
        assertEquals(Verdict.PRESENTATION_ERROR,outcome.verdict());
        assertEquals(Verdict.PRESENTATION_ERROR,outcome.caseResults().getFirst().status());
        assertEquals(0,outcome.score().signum());
        assertFalse(outcome.toString().contains("secret"));assertFalse(outcome.toString().contains("private"));
    }
    @Test void formatFailurePreservesPartialScoreButDoesNotHideContentFailure() {
        var partial=new JudgeEngine(new Fake(compiled(),result("Accepted",0,"2 \n"),result("Accepted",0,"4"))).judge(SOURCE,problem());
        assertEquals(Verdict.PARTIAL_ACCEPTED,partial.verdict());
        assertEquals(new BigDecimal("6.5"),partial.score());
        assertEquals(Verdict.PRESENTATION_ERROR,partial.caseResults().getFirst().status());
        var wrong=new JudgeEngine(new Fake(compiled(),result("Accepted",0,"2 \n"),result("Accepted",0,"999"))).judge(SOURCE,problem());
        assertEquals(Verdict.WRONG_ANSWER,wrong.verdict());
        assertEquals(0,wrong.score().signum());
    }
    @Test void missingFinalNewlinePassesAndReceivesFullScore() {
        var outcome=new JudgeEngine(new Fake(compiled(),result("Accepted",0,"2"),result("Accepted",0,"4"))).judge(SOURCE,problem());
        assertEquals(Verdict.ACCEPTED,outcome.verdict());
        assertEquals(0,BigDecimal.TEN.compareTo(outcome.score()));
    }
    @Test void compileFailureDoesNotRunCasesAndFiltersPaths() {
        var ce = new GoJudgeResult("Nonzero Exit Status", 1, 1L, 1L, 1L,
                Map.of("stderr", "/secret/compiler: failure\nmain.c:1: error: /private/file missing\n"),Map.of());
        Fake f = new Fake(ce);
        Outcome outcome = new JudgeEngine(f).judge(SOURCE, problem());
        assertEquals(Verdict.COMPILATION_ERROR, outcome.verdict());
        assertEquals(BigDecimal.ZERO, outcome.score());
        assertEquals(1, f.requests.size());
        assertFalse(outcome.compileMessage().contains("/secret"));
        assertFalse(outcome.compileMessage().contains("/private"));
        assertTrue(outcome.compileMessage().contains("main.c:1:"));
    }
    @Test void runtimeFailuresMapToDeterministicVerdicts() {
        Map<String,Verdict> statuses = Map.of("Time Limit Exceeded",Verdict.TIME_LIMIT_EXCEEDED,
                "Memory Limit Exceeded",Verdict.MEMORY_LIMIT_EXCEEDED,"Output Limit Exceeded",Verdict.OUTPUT_LIMIT_EXCEEDED,
                "Signalled",Verdict.RUNTIME_ERROR,"Nonzero Exit Status",Verdict.RUNTIME_ERROR,"Dangerous Syscall",Verdict.RUNTIME_ERROR);
        for (var pair : statuses.entrySet()) {
            Fake f = new Fake(compiled(), result(pair.getKey(),9,""),result(pair.getKey(),9,""));
            Outcome outcome = new JudgeEngine(f).judge(SOURCE,problem());
            assertEquals(pair.getValue(),outcome.verdict(),pair.getKey());
            assertEquals(0,outcome.score().signum());
            assertEquals(List.of("binary_1"),f.deleted);
        }
    }
    @Test void infrastructureFailureNeverAwardsPartialScore() {
        for (String status : List.of("Internal Error", "File Error", "unknown")) {
            Fake f = new Fake(compiled(),result("Accepted",0,"2\n"),result(status,0,""));
            Outcome outcome = new JudgeEngine(f).judge(SOURCE,problem());
            assertEquals(Verdict.SYSTEM_ERROR,outcome.verdict()); assertNull(outcome.score());
            assertEquals(1,outcome.caseResults().size()); assertEquals(List.of("binary_1"),f.deleted);
        }
    }
    @Test void transportFailureCleansCachedBinary() {
        Fake f = new Fake(compiled());
        Outcome outcome = new JudgeEngine(f).judge(SOURCE,problem());
        assertNull(outcome.score()); assertEquals("JUDGE_TRANSPORT_FAILURE",outcome.errorCode());
        assertEquals(List.of("binary_1"),f.deleted);
    }
    @Test void cleanupFailureIsVisibleAsSystemError() {
        Fake f = new Fake(compiled(), result("Accepted",0,"2\n"), result("Accepted",0,"4"));
        f.failCleanup = true;
        Outcome outcome = new JudgeEngine(f).judge(SOURCE,problem());
        assertNull(outcome.score()); assertEquals("JUDGE_CACHE_CLEANUP_FAILED",outcome.errorCode());
    }
    @Test void acceptedWithoutBinaryOrOutputIsSystemError() {
        assertTrue(new JudgeEngine(new Fake(result("Accepted",0,""))).judge(SOURCE,problem()).systemError());
        var missingOutput = new GoJudgeResult("Accepted",0,1L,1L,1L,Map.of(),Map.of());
        Fake f = new Fake(compiled(),missingOutput);
        assertTrue(new JudgeEngine(f).judge(SOURCE,problem()).systemError());
        assertEquals(List.of("binary_1"),f.deleted);
    }
    @Test void rejectInconsistentScoresAndDuplicateCases() {
        var test = problem().cases().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new ProblemSnapshot("v",BigDecimal.TEN,LIMITS,List.of(test)));
        assertThrows(IllegalArgumentException.class, () -> new ProblemSnapshot("v",new BigDecimal("7"),LIMITS,List.of(test,test)));
    }
    @Test void requestFactoryUsesFixedArgvAndResourceUnits() {
        Map<String,Object> req = GoJudgeRequests.compile("int main(){/* ; rm -rf / */return 0;}");
        Map<?,?> cmd = (Map<?,?>)((List<?>)req.get("cmd")).getFirst();
        assertEquals("/usr/bin/gcc", ((List<?>)cmd.get("args")).getFirst());
        assertFalse(((List<?>)cmd.get("args")).contains("sh"));
        assertEquals(10_000_000_000L,cmd.get("cpuLimit"));
        assertEquals(List.of("main?"),cmd.get("copyOutCached"));
        Map<?,?> run = (Map<?,?>)((List<?>)GoJudgeRequests.run("binary_1","1",LIMITS).get("cmd")).getFirst();
        assertEquals(1_000_000_000L,run.get("cpuLimit")); assertEquals(3_000_000_000L,run.get("clockLimit"));
        assertEquals(1,run.get("procLimit"));
    }
    @Test void rejectsInvalidIdsAndOversizedSource() {
        assertThrows(IllegalArgumentException.class,()->GoJudgeRequests.run("../private","",LIMITS));
        assertThrows(IllegalArgumentException.class,()->GoJudgeRequests.compile("中".repeat(30_000)));
        assertThrows(IllegalArgumentException.class,()->GoJudgeRequests.compile(" "));
        assertThrows(IllegalArgumentException.class,()->new Limits(0,100,1024,1024,1,1));
    }
}
