package com.cassist.problem;

import com.cassist.common.ApiExceptionHandler;
import com.cassist.identity.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssignmentUpdateControllerSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private TeachingService service;
    private MockMvc mvc;
    private static final String BODY="{\"title\":\"新作业\",\"opensAt\":\"2026-11-06T01:02:03Z\",\"closesAt\":\"2026-11-07T01:02:03Z\",\"aiEnabled\":false,\"chatEnabled\":true}";

    @Configuration @EnableWebMvc @EnableWebSecurity
    static class ConfigurationForTest {
        @Bean TeachingService teaching() {return mock(TeachingService.class);}
        @Bean ProblemLibraryService libraries() {return mock(ProblemLibraryService.class);}
        @Bean ObjectMapper objectMapper() {return JsonMapper.builder().findAndAddModules().build();}
    }
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(ConfigurationForTest.class,SecurityConfiguration.class,TeachingController.class,CurrentActor.class,ApiExceptionHandler.class);
        context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        service=context.getBean(TeachingService.class);
        when(service.updateAssignment(any(),eq("a"),any())).thenReturn(new TeachingService.AssignmentView("a","新作业","班级",
                Instant.parse("2026-11-06T01:02:03Z"),Instant.parse("2026-11-07T01:02:03Z"),List.of(),0,BigDecimal.ZERO,
                BigDecimal.ZERO,"PUBLISHED","FULL",0,0,false,true));
    }
    @AfterEach void close() {context.close();}
    private RequestPostProcessor signedIn(Role role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new Actor("actor",role),null,
                List.of(new SimpleGrantedAuthority("ROLE_"+role.name()))));
    }
    @Test void anonymousAndStudentCannotEdit() throws Exception {
        mvc.perform(put("/api/teacher/assignments/a").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/teacher/assignments/a").with(csrf()).with(signedIn(Role.STUDENT)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void authenticatedTeacherStillNeedsCsrf() throws Exception {
        mvc.perform(put("/api/teacher/assignments/a").with(signedIn(Role.TEACHER)).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void successfulPutReturnsExistingViewAndUsesOnlyTheAuthenticatedActor() throws Exception {
        mvc.perform(put("/api/teacher/assignments/a?userId=other").with(csrf()).with(signedIn(Role.TEACHER)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value("a")).andExpect(jsonPath("title").value("新作业"))
                .andExpect(jsonPath("aiEnabled").value(false)).andExpect(jsonPath("chatEnabled").value(true));
        verify(service).updateAssignment(new Actor("actor",Role.TEACHER),"a",new TeachingService.AssignmentUpdate("新作业",
                Instant.parse("2026-11-06T01:02:03Z"),Instant.parse("2026-11-07T01:02:03Z"),false,true));
    }
    @Test void malformedTimeIsRejectedBeforeService() throws Exception {
        mvc.perform(put("/api/teacher/assignments/a").with(csrf()).with(signedIn(Role.ADMIN)).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("2026-11-06T01:02:03Z","not-a-time"))).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
}
