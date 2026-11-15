package com.cassist.identity;
import com.cassist.common.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class PasswordSecurityTest {
    AnnotationConfigWebApplicationContext context;MockMvc mvc;JdbcTemplate jdbc;
    @Configuration @EnableWebMvc @EnableWebSecurity static class Config {
        @Bean JdbcTemplate jdbc(){return mock(JdbcTemplate.class);}
        @Bean PasswordSessions sessions(){return mock(PasswordSessions.class);}
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
    }
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class,SecurityConfiguration.class,PasswordController.class,CurrentActor.class,ApiExceptionHandler.class);
        context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();jdbc=context.getBean(JdbcTemplate.class);clearInvocations(jdbc);
    }
    @AfterEach void close(){context.close();}
    @Test void anonymousAndMissingCsrfRejected() throws Exception {
        for(String path:List.of("/api/auth/password","/api/admin/users/other/password")){
            mvc.perform(post(path).with(csrf()).contentType("application/json").content("{\"newPassword\":\"NewPassword456\"}")).andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new Actor("admin",Role.ADMIN),null,List.of()))).contentType("application/json").content("{\"newPassword\":\"NewPassword456\"}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(jdbc);
    }
    @Test void studentsAndTeachersCannotResetPasswords() throws Exception {
        for(Role role:List.of(Role.STUDENT,Role.TEACHER))
            mvc.perform(post("/api/admin/users/other/password").with(csrf()).with(authentication(UsernamePasswordAuthenticationToken.authenticated(new Actor("self",role),null,List.of()))).contentType("application/json").content("{\"newPassword\":\"NewPassword456\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }
}
