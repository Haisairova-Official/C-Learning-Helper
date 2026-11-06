package com.cassist.ai;

import com.cassist.ai.web.AiAnalysisController;
import com.cassist.common.*;
import com.cassist.identity.*;
import com.cassist.problem.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisControllerTest {
    private final CurrentActor actors = mock(CurrentActor.class);
    private final JsonJobStore jobs = mock(JsonJobStore.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ProblemAccessPort problems = mock(ProblemAccessPort.class);
    private final AiRuntime runtime = mock(AiRuntime.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final Actor student = new Actor("student-one", Role.STUDENT);
    private final AiAnalysisController controller = new AiAnalysisController(actors, jobs, jdbc, problems, runtime, profiles);
    private AiAnalysisController.Request request() { return new AiAnalysisController.Request("a", "p", "v", "int x;", null); }
    private void authorize() {
        when(actors.require()).thenReturn(student);
        when(jdbc.queryForObject("SELECT ai_enabled FROM assignment WHERE id=?",Boolean.class,"a")).thenReturn(true);
        when(runtime.mode()).thenReturn("DEEPSEEK");
        when(profiles.getAiReplyStyle(student.id())).thenReturn("GUIDED");
        when(problems.requireForStudent(student, "a", "p", "v")).thenReturn(new ProblemSnapshot(
                new StudentProblemView("p", "v", "求和", "题干", "输入", "输出", List.of(), 1000, 128, 65536, BigDecimal.TEN), "private-reference", List.of()));
    }
    @Test void unauthorizedProblemCannotCreateJobOrCallModel() {
        when(actors.require()).thenReturn(student);
        when(problems.requireForStudent(student, "a", "p", "v")).thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "拒绝"));
        assertThrows(BusinessException.class, () -> controller.create(request()));
        verifyNoInteractions(jobs, runtime);
    }
    @Test void returnsAcceptedBeforeModelRunsAndSanitizesFailure() throws Exception {
        authorize();
        AtomicReference<Runnable> background = new AtomicReference<>();
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); background.set(call.getArgument(2)); return null; })
                .when(runtime).submit(eq(student.id()), any(), any());
        var response = controller.create(request());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("RUNNING", response.getBody().get("status"));
        verify(runtime, never()).analyze(any(), any(), any(), any(), any(), any(), any());
        when(runtime.analyze(student, "a", "p", "v", "int x;", AiReplyStyle.GUIDED, "")).thenThrow(new IllegalStateException("sensitive-provider-error"));
        when(profiles.getAiReplyStyle(student.id())).thenReturn("DETAILED");
        background.get().run();
        verify(runtime).analyze(student, "a", "p", "v", "int x;", AiReplyStyle.GUIDED, "");
        verify(profiles,never()).getAiAddress(any());
        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(jobs).save(eq("ai_analysis"), eq(response.getBody().get("id")), eq("FAILED"), payload.capture());
        var json = JsonMapper.builder().build().valueToTree(payload.getValue());
        assertEquals("DEEPSEEK", json.at("/response/providerMode").asString());
        assertEquals("UNAVAILABLE", json.at("/response/errorCode").asString());
        assertEquals("GUIDED", json.at("/response/replyStyle").asString());
        assertFalse(json.toString().contains("sensitive-provider-error"));
        assertFalse(json.toString().contains("private-reference"));
    }
    @Test void publicProviderInfoRequiresLoginAndContainsNoCredentials() {
        when(actors.require()).thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请登录"));
        assertThrows(BusinessException.class, controller::provider);
        verifyNoInteractions(runtime);
    }
    @Test void disabledAssignmentCannotCreateJobOrCallModel() {
        authorize();
        when(jdbc.queryForObject("SELECT ai_enabled FROM assignment WHERE id=?",Boolean.class,"a")).thenReturn(false);
        var failure=assertThrows(BusinessException.class,()->controller.create(request()));
        assertEquals("AI_DISABLED",failure.code());
        verifyNoInteractions(jobs);
        verify(runtime,never()).submit(any(),any(),any());
    }
    @Test void tinoStyleAndAddressAreCapturedWhenAcceptedDespiteLaterProfileChanges() throws Exception {
        authorize();
        when(profiles.getAiReplyStyle(student.id())).thenReturn("TINO");
        when(profiles.getAiAddress(student)).thenReturn("Test User");
        AtomicReference<Runnable> background=new AtomicReference<>();
        doAnswer(call->{call.getArgument(1,Runnable.class).run();background.set(call.getArgument(2));return null;})
                .when(runtime).submit(eq(student.id()),any(),any());
        var accepted=controller.create(request());
        when(profiles.getAiReplyStyle(student.id())).thenReturn("CONCISE");
        when(profiles.getAiAddress(student)).thenReturn("另一个昵称");
        when(runtime.analyze(student,"a","p","v","int x;",AiReplyStyle.TINO,"Test User")).thenReturn(
                new AiGuidanceCore.Result(accepted.getBody().get("codeHash"),GuidancePrompt.VERSION,AiProvider.ProviderMode.DEEPSEEK,
                        new AiProvider.Guidance("Test User，我们先核对输入边界。",List.of())));
        background.get().run();
        verify(runtime).analyze(student,"a","p","v","int x;",AiReplyStyle.TINO,"Test User");
        verify(profiles,times(1)).getAiAddress(student);
        var payload=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(jobs).save(eq("ai_analysis"),eq(accepted.getBody().get("id")),eq("COMPLETED"),payload.capture());
        var json=JsonMapper.builder().build().valueToTree(payload.getValue());
        assertEquals("TINO",json.at("/response/replyStyle").asString());
        assertEquals("Test User",json.path("addressName").asString());
        assertFalse(json.path("response").has("addressName"));
        assertFalse(json.toString().contains("private-reference"));
    }
}
