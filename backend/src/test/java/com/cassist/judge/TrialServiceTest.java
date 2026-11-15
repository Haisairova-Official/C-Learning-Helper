package com.cassist.judge;
import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import com.cassist.problem.ProblemAccessPort;
import com.cassist.judge.core.*;
import com.cassist.judge.submission.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TrialServiceTest {
    final Actor actor=new Actor("student",Role.STUDENT);
    final ProblemAccessPort problems=mock(ProblemAccessPort.class);
    final JudgeRuntime runtime=mock(JudgeRuntime.class);
    final JudgeEngine engine=mock(JudgeEngine.class);
    final TrialService service=new TrialService(problems,runtime);
    final JudgeEngine.TrialResult result=new JudgeEngine.TrialResult(Verdict.ACCEPTED,"42","","",1L,1L);
    @BeforeEach void setup(){when(problems.requireForSubmission(eq(actor),anyString(),anyString(),anyString(),any())).thenReturn(FunctionProblemTest.snapshot("PROGRAM","C17",""));when(runtime.engine()).thenReturn(engine);when(engine.trial(any(),anyString(),any(),nullable(String.class))).thenReturn(result);}
    @AfterEach void close(){service.close();}
    TrialService.Input input(String code,String language,String stdin,String driver){return new TrialService.Input("a","p","v",code,language,stdin,null,driver);}
    TrialService.View await(String id) throws Exception {
        for(int i=0;i<100;i++){var v=service.get(actor,id);if(v.result()!=null)return v;Thread.sleep(10);}throw new AssertionError("not completed");
    }
    @Test void onlyStudentsAndAuthorizedVersionsReachRuntime(){
        assertThrows(BusinessException.class,()->service.start(new Actor("teacher",Role.TEACHER),input("int main(){}","C17","","")));
        verifyNoInteractions(problems,runtime);
        when(problems.requireForSubmission(eq(actor),anyString(),anyString(),anyString(),any())).thenThrow(new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,"NOT_FOUND","not found"));
        assertThrows(BusinessException.class,()->service.start(actor,input("int main(){}","C17","","")));verifyNoInteractions(runtime);
    }
    @Test void languageAndUtf8ByteLimitsAreEnforcedBeforeCompiler(){
        for(var i:java.util.List.of(input("int main(){}","CPP17","",""),input("x".repeat(65537),"C17","",""),input("main","C17","中".repeat(22000),"")))
            assertThrows(BusinessException.class,()->service.start(actor,i));
        verifyNoInteractions(runtime);
    }
    @Test void functionUsesOnlyStudentCodeAndOwnDriverNeverPrivateSnapshot() throws Exception {
        when(problems.requireForSubmission(eq(actor),anyString(),anyString(),anyString(),any())).thenReturn(FunctionProblemTest.snapshot("FUNCTION","C17",FunctionProblemTest.TEMPLATE));
        String code="int add(int a,int b){return a+b;}";String driver="int main(){return add(1,2);}";
        var view=service.start(actor,input(code,"C17","user-input",driver));await(view.id());
        verify(engine).trial(argThat(s->s.content().equals(code+"\n"+driver) && !s.hasPrivateTemplate() && !s.content().contains("PRIVATE")),eq("user-input"),any(),isNull());
    }
    @Test void functionRequiresOwnDriver(){
        when(problems.requireForSubmission(eq(actor),anyString(),anyString(),anyString(),any())).thenReturn(FunctionProblemTest.snapshot("FUNCTION","C17",FunctionProblemTest.TEMPLATE));
        assertThrows(BusinessException.class,()->service.start(actor,input("int add(){}","C17","","")));verifyNoInteractions(runtime);
    }
    @Test void resultsAreOwnerOnly() throws Exception {
        var job=service.start(actor,input("int main(){}","C17","",""));assertEquals(result,await(job.id()).result());
        assertThrows(BusinessException.class,()->service.get(new Actor("other",Role.STUDENT),job.id()));
        assertThrows(BusinessException.class,()->service.get(actor,"unknown"));
    }
    @Test void rejectsConcurrentTestBySameStudentAndReleasesSlot() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(engine.trial(any(),anyString(),any(),nullable(String.class))).thenAnswer(inv->{entered.countDown();release.await(3,TimeUnit.SECONDS);return result;});
        var job=service.start(actor,input("int main(){}","C17","",""));assertTrue(entered.await(2,TimeUnit.SECONDS));
        var error=assertThrows(BusinessException.class,()->service.start(actor,input("int main(){}","C17","","")));assertEquals("TRIAL_BUSY",error.code());
        release.countDown();await(job.id());
    }
}
