package com.cassist.judge;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import com.cassist.judge.submission.*;
import com.cassist.judge.submission.SubmissionModels.CreateRequest;
import com.cassist.problem.*;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FunctionSubmissionAuthorizationTest {
    final Actor student=new Actor("student",Role.STUDENT);
    final SubmissionRepository repository=mock(SubmissionRepository.class);
    final ProblemAccessPort problems=mock(ProblemAccessPort.class);
    final AccessPolicy access=mock(AccessPolicy.class);
    final JudgeRuntime runtime=mock(JudgeRuntime.class);
    final SubmissionService service=new SubmissionService(repository,problems,access,runtime);

    @Test void rejectsLanguageMismatchBeforeQueueOrCompiler() {
        when(repository.findByKey(anyString(),anyString())).thenReturn(Optional.empty());
        when(problems.requireForSubmission(eq(student),eq("a"),eq("p"),eq("v"),any()))
                .thenReturn(FunctionProblemTest.snapshot("FUNCTION","C17",FunctionProblemTest.TEMPLATE));
        var error=assertThrows(BusinessException.class,()->service.submit(student,
                new CreateRequest("a","p","v","int add(int a,int b){return a+b;}","CPP17"),"function-test-key-01"));
        assertEquals("INVALID_SUBMISSION",error.code());
        verify(repository,never()).create(anyString(),any(),anyString(),anyString(),anyString(),any(),any());
        verifyNoInteractions(runtime);
    }

    @Test void unauthorizedVersionNeverReachesCompositionOrQueue() {
        when(repository.findByKey(anyString(),anyString())).thenReturn(Optional.empty());
        when(problems.requireForSubmission(eq(student),eq("a"),eq("p"),eq("other-version"),any()))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND","题目不存在"));
        var error=assertThrows(BusinessException.class,()->service.submit(student,
                new CreateRequest("a","p","other-version","int add(int a,int b){return a+b;}","C17"),"function-test-key-02"));
        assertEquals(HttpStatus.NOT_FOUND,error.status());
        verify(repository,never()).create(anyString(),any(),anyString(),anyString(),anyString(),any(),any());
        verifyNoInteractions(runtime);
    }

    @Test void acceptedFunctionPersistsOriginalRequestAndNeverPrivateCombinedSource() {
        String source="int add(int a,int b){return a+b;}";
        var request=new CreateRequest("a","p","v",source,"CPP17");
        when(repository.findByKey(anyString(),anyString())).thenReturn(Optional.empty());
        when(problems.requireForSubmission(eq(student),eq("a"),eq("p"),eq("v"),any()))
                .thenReturn(FunctionProblemTest.snapshot("FUNCTION","CPP17",FunctionProblemTest.TEMPLATE));
        when(repository.create(eq(student.id()),same(request),anyString(),anyString(),anyString(),eq(BigDecimal.TEN),any()))
                .thenReturn(new SubmissionModels.Row("s",student.id(),"a","p","v",source,"CPP17","QUEUED",null,null,
                        BigDecimal.TEN,"","hash",Instant.now(),null));
        assertEquals("s",service.submit(student,request,"function-test-key-03").id());
        verify(repository).create(eq(student.id()),same(request),anyString(),anyString(),anyString(),eq(BigDecimal.TEN),any());
        verify(runtime).requireAvailable();
    }
}
