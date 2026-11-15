package com.cassist.identity;
import com.cassist.common.ApiExceptionHandler;
import com.cassist.judge.submission.*;
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
class TrialSecurityTest {
    AnnotationConfigWebApplicationContext context;MockMvc mvc;JdbcTemplate jdbc;
    @Configuration @EnableWebMvc @EnableWebSecurity static class Config {
        @Bean JdbcTemplate jdbc(){return mock(JdbcTemplate.class);}
        @Bean TrialService trials(){return mock(TrialService.class);}
        @Bean PasswordSessions sessions(){return mock(PasswordSessions.class);}
        @Bean ObjectMapper mapper(){return JsonMapper.builder().findAndAddModules().build();}
    }
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class,SecurityConfiguration.class,TrialController.class,CurrentActor.class,ApiExceptionHandler.class);
        context.refresh();mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();jdbc=context.getBean(JdbcTemplate.class);clearInvocations(jdbc);
    }
    @AfterEach void close(){context.close();}
    @Test void customRunRequiresLoginAndCsrf() throws Exception {
        String body="{\"assignmentId\":\"a\",\"problemId\":\"p\",\"problemVersionId\":\"v\",\"sourceCode\":\"int main(){}\",\"language\":\"C17\",\"stdin\":\"\"}";
        mvc.perform(post("/api/trials").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trials/other")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/trials").with(authentication(UsernamePasswordAuthenticationToken.authenticated(new Actor("self",Role.STUDENT),null,List.of()))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(TrialService.class));
    }
}
