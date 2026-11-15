package com.cassist.judge;
import com.cassist.judge.core.*;
import com.cassist.judge.protocol.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrialEngineTest {
    final CompilationSource source=CompilationSource.program("int main(){return 0;}",CompileLanguage.C17);
    @Test void returnsOnlyCustomOutputAndCleansBinary(){
        var transport=new JudgeCoreTest.Fake(JudgeCoreTest.compiled(),JudgeCoreTest.result("Accepted",0,"42\n"));
        var result=new JudgeEngine(transport).trial(source,"custom input",JudgeCoreTest.LIMITS,null);
        assertEquals(Verdict.ACCEPTED,result.verdict());assertEquals("42\n",result.stdout());assertEquals(2,result.timeMs());assertEquals(2,result.memoryKb());
        assertTrue(transport.requests.get(1).toString().contains("custom input"));assertEquals(List.of("binary_1"),transport.deleted);
    }
    @Test void supportsExpectedEmptyTextAndDifferenceAndFormatting(){
        for(var test:List.of(new String[]{"","", "ACCEPTED"},new String[]{"2","3","WRONG_ANSWER"},new String[]{"2 3","2  3","PRESENTATION_ERROR"},new String[]{"2\r\n","2","ACCEPTED"})){
            var transport=new JudgeCoreTest.Fake(JudgeCoreTest.compiled(),JudgeCoreTest.result("Accepted",0,test[1]));
            assertEquals(Verdict.valueOf(test[2]),new JudgeEngine(transport).trial(source,"",JudgeCoreTest.LIMITS,test[0]).verdict());
        }
    }
    @Test void compileFailureNeverRuns(){
        var transport=new JudgeCoreTest.Fake(new GoJudgeResult("Nonzero Exit Status",1,1L,1L,1L,Map.of("stderr","main.c:2: error: syntax"),Map.of()));
        var r=new JudgeEngine(transport).trial(source,"",JudgeCoreTest.LIMITS,null);
        assertEquals(Verdict.COMPILATION_ERROR,r.verdict());assertTrue(r.compileMessage().contains("syntax"));assertEquals(1,transport.requests.size());
    }
    @Test void runtimeFailuresRetainPartialOutputAndCleanup(){
        for(String status:List.of("Time Limit Exceeded","Memory Limit Exceeded","Output Limit Exceeded","Nonzero Exit Status")){
            var transport=new JudgeCoreTest.Fake(JudgeCoreTest.compiled(),JudgeCoreTest.result(status,1,"partial"));
            var r=new JudgeEngine(transport).trial(source,"",JudgeCoreTest.LIMITS,"partial");
            assertNotEquals(Verdict.ACCEPTED,r.verdict());assertEquals("partial",r.stdout());assertEquals(List.of("binary_1"),transport.deleted);
        }
    }
    @Test void transportAndCleanupFailureDoNotExposeDetails(){
        var transport=new JudgeCoreTest.Fake(JudgeCoreTest.compiled());
        assertEquals(Verdict.SYSTEM_ERROR,new JudgeEngine(transport).trial(source,"",JudgeCoreTest.LIMITS,null).verdict());
        assertEquals(List.of("binary_1"),transport.deleted);
        var other=new JudgeCoreTest.Fake(JudgeCoreTest.compiled(),JudgeCoreTest.result("Accepted",0,""));other.failCleanup=true;
        assertEquals(Verdict.SYSTEM_ERROR,new JudgeEngine(other).trial(source,"",JudgeCoreTest.LIMITS,null).verdict());
    }
    @Test void privateHarnessIsNeverExecuted(){
        var transport=new JudgeCoreTest.Fake();
        assertThrows(IllegalArgumentException.class,()->new JudgeEngine(transport).trial(new CompilationSource("PRIVATE",CompileLanguage.C17,true),"",JudgeCoreTest.LIMITS,null));
        assertTrue(transport.requests.isEmpty());
    }
}
