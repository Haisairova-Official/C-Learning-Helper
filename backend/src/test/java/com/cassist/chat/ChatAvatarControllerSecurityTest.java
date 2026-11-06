package com.cassist.chat;

import com.cassist.common.*;
import com.cassist.identity.*;
import com.cassist.problem.*;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatAvatarControllerSecurityTest {
    private static final String ROOM="/api/assignments/a/problems/p/chat",AVATAR=ROOM+"/participants/author/avatar";
    private static final String AVATAR_SQL="SELECT u.avatar_png FROM app_user u WHERE u.id=? AND u.avatar_png IS NOT NULL AND EXISTS(SELECT 1 FROM problem_chat_message m WHERE m.author_id=u.id AND m.assignment_id=? AND m.problem_version_id=?)";
    private static final byte[] PNG={(byte)137,80,78,71};
    private final Actor student=new Actor("student",Role.STUDENT),teacher=new Actor("teacher",Role.TEACHER);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private TeachingService teaching;
    private AssignmentSelectionService selection;
    @Configuration @EnableWebMvc @EnableWebSecurity
    static class ConfigurationForTest {
        @Bean JdbcTemplate jdbc(){return mock(JdbcTemplate.class);}
        @Bean TeachingService teaching(){return mock(TeachingService.class);}
        @Bean AssignmentSelectionService selection(){return mock(AssignmentSelectionService.class);}
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
    }
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(ConfigurationForTest.class,SecurityConfiguration.class,CurrentActor.class,ProblemChatController.class,ApiExceptionHandler.class);
        context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc=context.getBean(JdbcTemplate.class);teaching=context.getBean(TeachingService.class);selection=context.getBean(AssignmentSelectionService.class);
        StudentProblemView problem=mock(StudentProblemView.class);when(problem.versionId()).thenReturn("version");
        when(teaching.studentProblem(any(Actor.class),eq("a"),eq("p"))).thenReturn(problem);
        when(selection.options("a")).thenReturn(new AssignmentSelectionService.Options("FULL",1,true,true));
        when(jdbc.query(eq(AVATAR_SQL),any(RowMapper.class),eq("author"),eq("a"),eq("version"))).thenReturn(List.of(PNG));
        clearInvocations(jdbc); // Exclude Spring's bean initialization from request-boundary assertions.
    }
    @AfterEach void close(){context.close();}
    private RequestPostProcessor signedIn(Actor actor) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,
                List.of(new SimpleGrantedAuthority("ROLE_"+actor.role().name()))));
    }
    @Test void anonymousCannotReadAnyAvatar() throws Exception {
        mvc.perform(get(AVATAR)).andExpect(status().isUnauthorized());
        verifyNoInteractions(teaching,jdbc);
    }
    @Test void nonMemberAndUnrelatedTeacherAreDeniedBeforeAvatarQuery() throws Exception {
        for(Actor actor:List.of(new Actor("nonmember",Role.STUDENT),new Actor("other",Role.TEACHER))) {
            when(teaching.studentProblem(actor,"a","p")).thenThrow(new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","无权访问该资源"));
            mvc.perform(get(AVATAR).with(signedIn(actor))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(jdbc);
    }
    @Test void unassignedRandomQuestionDoesNotRevealAvatar() throws Exception {
        when(teaching.studentProblem(student,"a","p")).thenThrow(new BusinessException(HttpStatus.NOT_FOUND,"NOT_FOUND","资源不存在"));
        mvc.perform(get(AVATAR).with(signedIn(student))).andExpect(status().isNotFound());
        verifyNoInteractions(jdbc);
    }
    @Test void studentCannotReadAvatarWhenDiscussionIsClosed() throws Exception {
        when(selection.options("a")).thenReturn(new AssignmentSelectionService.Options("FULL",1,true,false));
        mvc.perform(get(AVATAR).with(signedIn(student))).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CHAT_DISABLED"));
        verifyNoInteractions(jdbc);
    }
    @Test void missingAvatarOrAuthorOutsideThisRoomReturns404() throws Exception {
        when(jdbc.query(eq(AVATAR_SQL),any(RowMapper.class),eq("author"),eq("a"),eq("version"))).thenReturn(List.of());
        mvc.perform(get(AVATAR).with(signedIn(student))).andExpect(status().isNotFound());
        mvc.perform(get(ROOM+"/participants/outside/avatar").with(signedIn(student))).andExpect(status().isNotFound());
        verify(jdbc).query(eq(AVATAR_SQL),any(RowMapper.class),eq("outside"),eq("a"),eq("version"));
    }
    @Test void returnsOnlyPngWithPrivateNoStoreHeaders() throws Exception {
        mvc.perform(get(AVATAR+"?v=123-456&userId=another").with(signedIn(student)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG)).andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control",containsString("private"))).andExpect(header().string("Cache-Control",containsString("no-store")))
                .andExpect(header().string("Vary","Cookie")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        verify(jdbc).query(eq(AVATAR_SQL),any(RowMapper.class),eq("author"),eq("a"),eq("version"));
    }
    @Test void authorizedTeacherRetainsHistoryAvatarAccessWithDiscussionClosed() throws Exception {
        when(selection.options("a")).thenReturn(new AssignmentSelectionService.Options("FULL",1,true,false));
        mvc.perform(get(AVATAR).with(signedIn(teacher))).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        verify(teaching).studentProblem(teacher,"a","p");
    }
    @Test void messagesUseSharedVersionedAuthorUrlAndNullWhenAvatarAbsent() throws Exception {
        AtomicReference<LocalDateTime> updated=new AtomicReference<>(LocalDateTime.parse("2026-11-06T01:02:03.123456"));
        when(jdbc.query(contains("COALESCE(NULLIF(u.nickname"),any(RowMapper.class),eq("a"),eq("version"),eq(0L))).thenAnswer(call->{
            RowMapper<?> mapper=call.getArgument(1);
            return new ArrayList<>(List.of(mapper.mapRow(message("3",false,updated.get()),0),
                    mapper.mapRow(message("2",true,updated.get()),1),mapper.mapRow(message("1",true,updated.get()),2)));
        });
        when(jdbc.queryForObject(contains("assignment_chat_mute"),eq(Integer.class),eq("a"),eq("student"))).thenReturn(0);
        String first=AVATAR+"?v="+updated.get().toEpochSecond(ZoneOffset.UTC)+"-"+updated.get().getNano();
        mvc.perform(get(ROOM).with(signedIn(student))).andExpect(status().isOk())
                .andExpect(jsonPath("items[0].authorAvatarUrl").value(first)).andExpect(jsonPath("items[1].authorAvatarUrl").value(first))
                .andExpect(jsonPath("items[2].authorAvatarUrl").value(nullValue()));
        updated.set(updated.get().plusSeconds(1));
        String second=AVATAR+"?v="+updated.get().toEpochSecond(ZoneOffset.UTC)+"-"+updated.get().getNano();
        mvc.perform(get(ROOM).with(signedIn(student))).andExpect(status().isOk()).andExpect(jsonPath("items[0].authorAvatarUrl").value(second));
    }
    private ResultSet message(String id,boolean hasAvatar,LocalDateTime updated) throws Exception {
        ResultSet row=mock(ResultSet.class);
        when(row.getString(1)).thenReturn(id);when(row.getString(2)).thenReturn(hasAvatar?"author":"without-avatar");
        when(row.getString(3)).thenReturn("昵称");when(row.getString(4)).thenReturn("STUDENT");when(row.getString(5)).thenReturn("正文");
        when(row.getObject(6,LocalDateTime.class)).thenReturn(updated.minusHours(1));when(row.getBoolean(7)).thenReturn(false);
        when(row.getBoolean(8)).thenReturn(hasAvatar);when(row.getObject(9,LocalDateTime.class)).thenReturn(updated);return row;
    }
}
