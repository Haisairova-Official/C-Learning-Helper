package com.cassist.problem;

import com.cassist.common.BusinessException;
import com.cassist.identity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssignmentUpdateServiceTest {
    private static final Instant OPEN=Instant.parse("2026-11-06T01:02:03.123456Z");
    private static final Instant CLOSE=Instant.parse("2026-11-07T01:02:03.654321Z");
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final TeachingService service=spy(new TeachingService(jdbc,new AccessPolicy(jdbc),
            JsonMapper.builder().findAndAddModules().build(),mock(AssignmentSelectionService.class)));
    private final Actor owner=new Actor("owner",Role.TEACHER);
    private final TeachingService.AssignmentView view=new TeachingService.AssignmentView("a","新作业","班级",OPEN,CLOSE,
            List.of(new TeachingService.ProblemSummary("p","frozen-version","题目",BigDecimal.TEN,null)),0,BigDecimal.ZERO,
            BigDecimal.TEN,"PUBLISHED","PER_STUDENT_RANDOM",1,2,false,true);

    @BeforeEach void setup() {
        when(jdbc.queryForList("SELECT owner_id FROM assignment WHERE id = ?",String.class,"a")).thenReturn(List.of("owner"));
        doReturn(view).when(service).assignmentView(any(Actor.class),eq("a"));
    }
    private static TeachingService.AssignmentUpdate input(String title,Instant opens,Instant closes,Boolean ai,Boolean chat) {
        return new TeachingService.AssignmentUpdate(title,opens,closes,ai,chat);
    }

    @Test void owningTeacherUpdatesOnlyEditableColumnsAndKeepsMicroseconds() {
        assertSame(view,service.updateAssignment(owner,"a",input("  新作业  ",OPEN,CLOSE,false,true)));
        verify(jdbc).update("UPDATE assignment SET title=?,opens_at=?,closes_at=?,ai_enabled=?,chat_enabled=? WHERE id=?",
                "新作业",LocalDateTime.parse("2026-11-06T01:02:03.123456"),LocalDateTime.parse("2026-11-07T01:02:03.654321"),false,true,"a");
        verify(jdbc,times(1)).update(anyString(),any(Object[].class));
    }

    @Test void administratorMayEditAssignmentOwnedByAnotherTeacher() {
        assertSame(view,service.updateAssignment(new Actor("admin",Role.ADMIN),"a",input("新作业",OPEN,CLOSE,true,false)));
        verify(jdbc,times(1)).update(anyString(),any(Object[].class));
    }

    @Test void studentAndUnrelatedTeacherCannotUpdateAnyColumns() {
        for(Actor actor:List.of(new Actor("student",Role.STUDENT),new Actor("other",Role.TEACHER))) {
            var error=assertThrows(BusinessException.class,()->service.updateAssignment(actor,"a",input("新作业",OPEN,CLOSE,true,false)));
            assertEquals(HttpStatus.FORBIDDEN,error.status());
        }
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void missingAssignmentReturns404BeforeWriting() {
        when(jdbc.queryForList("SELECT owner_id FROM assignment WHERE id = ?",String.class,"a")).thenReturn(List.of());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(BusinessException.class,
                ()->service.updateAssignment(owner,"a",input("新作业",OPEN,CLOSE,true,false))).status());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void acceptsTwoHundredUnicodeCharacters() {
        service.updateAssignment(owner,"a",input("🧪".repeat(200),OPEN,CLOSE,true,false));
        verify(jdbc,times(1)).update(anyString(),any(Object[].class));
    }

    static Stream<TeachingService.AssignmentUpdate> invalidRequests() {
        return Stream.of(null,input(null,OPEN,CLOSE,true,false),input(" \n ",OPEN,CLOSE,true,false),
                input("题".repeat(201),OPEN,CLOSE,true,false),input("题",null,CLOSE,true,false),input("题",OPEN,null,true,false),
                input("题",OPEN,OPEN,true,false),input("题",CLOSE,OPEN,true,false),input("题",OPEN,CLOSE,null,false),
                input("题",OPEN,CLOSE,true,null),input("题",Instant.parse("0999-01-01T00:00:00Z"),CLOSE,true,false),
                input("题",OPEN,Instant.parse("+10000-01-01T00:00:00Z"),true,false),
                input("题",OPEN,OPEN.plusNanos(1),true,false));
    }

    @ParameterizedTest @MethodSource("invalidRequests")
    void rejectsIncompleteInvalidAndUnrepresentableParametersBeforeWriting(TeachingService.AssignmentUpdate request) {
        var error=assertThrows(BusinessException.class,()->service.updateAssignment(owner,"a",request));
        assertEquals(HttpStatus.BAD_REQUEST,error.status());
        assertEquals("VALIDATION_FAILED",error.code());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
