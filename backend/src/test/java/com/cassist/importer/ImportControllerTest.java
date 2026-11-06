package com.cassist.importer;

import com.cassist.common.*;
import com.cassist.identity.*;
import com.cassist.importer.web.ImportController;
import com.cassist.problem.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportControllerTest {
    private final Actor teacher=new Actor("teacher",Role.TEACHER);
    private final CurrentActor actors=mock(CurrentActor.class);
    private final JsonJobStore jobs=mock(JsonJobStore.class);
    private final DraftImportPort port=mock(DraftImportPort.class);
    private final ImportController controller=new ImportController(actors,jobs,port);
    @Test @SuppressWarnings("unchecked") void realExampleBundleAndArrayBothParseAndCommitWithoutLosingFields() throws Exception {
        Path example=Path.of(System.getProperty("basedir",System.getProperty("user.dir")),"../examples/pta/function-insert.json").normalize();
        var json=JsonMapper.builder().build(); var bundle=json.readTree(Files.readString(example));
        for(String source:List.of(bundle.toString(),bundle.get("problems").toString())) {
            reset(jobs,port); when(actors.requireProblemManager()).thenReturn(teacher);
            controller.parse(new ImportController.TextRequest("JSON",source));
            ArgumentCaptor<ImportController.Job> preview=ArgumentCaptor.forClass(ImportController.Job.class);
            verify(jobs).create(eq("import_job"),anyString(),eq(teacher),eq("READY"),preview.capture());
            var job=preview.getValue(); assertEquals(2,job.candidates().size());
            assertTrue(job.candidates().stream().allMatch(c->c.unresolvedContent().isBlank()));
            when(jobs.read("import_job",job.id(),teacher,ImportController.Job.class)).thenReturn(job);
            when(port.saveDrafts(eq(teacher),eq(job.id()),anyList(),eq("样例"))).thenReturn(new DraftImportPort.DraftImportResult("set-1",List.of("c","cpp")));
            controller.commit(job.id(),"sample-bundle-import-001",new ImportController.CommitRequest("样例",job.candidates()));
            ArgumentCaptor<List<DraftProblem>> captured=ArgumentCaptor.forClass(List.class);
            verify(port).saveDrafts(eq(teacher),eq(job.id()),captured.capture(),eq("样例"));
            assertEquals(List.of("C17","CPP17"),captured.getValue().stream().map(DraftProblem::language).toList());
            for(int i=0;i<2;i++) {
                var saved=captured.getValue().get(i); var original=bundle.get("problems").get(i);
                assertEquals("FUNCTION",saved.problemType()); assertEquals(16384L,saved.codeLimitBytes()); assertEquals(10,saved.testCases().size());
                assertEquals(original.get("judgeTemplate").asString(),saved.judgeTemplate());
                assertEquals(original.get("starterCode").asString(),saved.starterCode());
                assertEquals(original.get("functionSignature").asString(),saved.functionSignature());
                assertEquals(original.get("referenceAnswer").asString(),saved.referenceAnswer());
                assertEquals(original.get("samples").get(0).get("explanation").asString(),saved.samples().getFirst().explanation());
            }
        }
    }
    @Test void jsonRequestPersistsPrivateStructuredPreview() {
        when(actors.requireProblemManager()).thenReturn(teacher);
        var result=controller.parse(new ImportController.TextRequest("JSON","{\"title\":\"函数题\",\"problemType\":\"FUNCTION\",\"language\":\"CPP17\",\"judgeTemplate\":\"/*__STUDENT_CODE__*/ private harness\"}"));
        assertEquals(HttpStatus.ACCEPTED,result.getStatusCode());
        ArgumentCaptor<ImportController.Job> captor=ArgumentCaptor.forClass(ImportController.Job.class);
        verify(jobs).create(eq("import_job"),anyString(),eq(teacher),eq("READY"),captor.capture());
        var candidate=captor.getValue().candidates().getFirst();
        assertEquals("CPP17",candidate.language()); assertEquals("FUNCTION",candidate.problemType());
        assertTrue(candidate.judgeTemplate().contains("private harness")); assertFalse(candidate.statement().contains("private harness"));
    }
    @Test @SuppressWarnings("unchecked") void functionImportRetainsPrivateTemplateAndAllowsIncompleteDrafts() {
        for(String template:List.of("","/*__STUDENT_CODE__*/ private harness")) {
            reset(port);
            var c=new ImportPreview.Candidate("c1","函数草稿","题干","","",List.of(),"private answer","","",List.of(),BigDecimal.TEN,1000,128,new ImportPreview.SourceLocation(1,1),List.of(),true,"FUNCTION","CPP17","","public starter",template);
            setup(c); when(port.saveDrafts(eq(teacher),eq("job-1"),anyList(),eq("练习"))).thenReturn(new DraftImportPort.DraftImportResult("set-1",List.of("problem-1")));
            controller.commit("job-1","same-request-key-0001",new ImportController.CommitRequest("练习",List.of(c)));
            ArgumentCaptor<List<DraftProblem>> captor=ArgumentCaptor.forClass(List.class);
            verify(port).saveDrafts(eq(teacher),eq("job-1"),captor.capture(),eq("练习"));
            var saved=captor.getValue().getFirst(); assertEquals("FUNCTION",saved.problemType()); assertEquals("CPP17",saved.language());
            assertEquals(template,saved.judgeTemplate()); assertEquals("public starter",saved.starterCode()); assertEquals("",saved.functionSignature());
        }
    }
    @Test void malformedNonemptyFunctionTemplateCannotBeCommitted() {
        var c=new ImportPreview.Candidate("c1","函数草稿","题干","","",List.of(),"","","",List.of(),BigDecimal.TEN,1000,128,new ImportPreview.SourceLocation(1,1),List.of(),true,"FUNCTION","C17","int f();","","/*__STUDENT_CODE__*//*__STUDENT_CODE__*/");
        setup(c);
        assertThrows(BusinessException.class,()->controller.commit("job-1","same-request-key-0001",new ImportController.CommitRequest("练习",List.of(c))));
        verifyNoInteractions(port);
    }
    @Test void studentCannotParsePrivateMaterial() {
        when(actors.requireProblemManager()).thenThrow(new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","教师权限"));
        assertThrows(BusinessException.class,()->controller.parse(new ImportController.TextRequest("TEXT","第1题：题干")));
        verifyNoInteractions(jobs,port);
    }
    @Test void unresolvedMaterialCannotBeCommitted() {
        var candidate=candidate("用途不明的参考代码",List.of()); setup(candidate);
        assertThrows(BusinessException.class,()->controller.commit("job-1","same-request-key-0001",new ImportController.CommitRequest("练习",List.of(candidate))));
        verifyNoInteractions(port);
    }
    @Test void rejectsCandidateNotPresentInOriginalParse() {
        var candidate=candidate("",List.of());
        when(actors.requireProblemManager()).thenReturn(teacher);
        when(jobs.read("import_job","job-1",teacher,ImportController.Job.class)).thenReturn(new ImportController.Job("job-1","READY","test",List.of(),List.of()));
        assertThrows(BusinessException.class,()->controller.commit("job-1","same-request-key-0001",new ImportController.CommitRequest("练习",List.of(candidate))));
        verifyNoInteractions(port);
    }
    @Test @SuppressWarnings("unchecked") void replayProducesStableDraftContentsAndTestIds() {
        var candidate=candidate("",List.of(new ImportPreview.TestCase("1","1 2","3","HIDDEN",new BigDecimal("10"),"TOKENS")));
        setup(candidate);
        when(port.saveDrafts(eq(teacher),eq("job-1"),anyList(),eq("练习"))).thenReturn(new DraftImportPort.DraftImportResult("set-1",List.of("problem-1")));
        var input=new ImportController.CommitRequest("练习",List.of(candidate));
        controller.commit("job-1","same-request-key-0001",input);
        controller.commit("job-1","same-request-key-0001",input);
        ArgumentCaptor<List<DraftProblem>> captor=ArgumentCaptor.forClass(List.class);
        verify(port,times(2)).saveDrafts(eq(teacher),eq("job-1"),captor.capture(),eq("练习"));
        assertEquals(captor.getAllValues().get(0),captor.getAllValues().get(1));
        assertEquals("private reference",captor.getValue().getFirst().referenceAnswer());
        assertFalse(captor.getValue().getFirst().statement().contains("private reference"));
    }
    private void setup(ImportPreview.Candidate c) {
        when(actors.requireProblemManager()).thenReturn(teacher);
        when(jobs.read("import_job","job-1",teacher,ImportController.Job.class)).thenReturn(new ImportController.Job("job-1","READY","test",List.of(c),List.of()));
    }
    private ImportPreview.Candidate candidate(String unresolved,List<ImportPreview.TestCase> tests) {
        return new ImportPreview.Candidate("c1","求和","计算两个整数的和","两个整数","和",List.of(new ImportPreview.Sample("1","1 2","3")),"private reference","",unresolved,tests,new BigDecimal("10"),1000,128,new ImportPreview.SourceLocation(1,10),List.of(),true);
    }
}
