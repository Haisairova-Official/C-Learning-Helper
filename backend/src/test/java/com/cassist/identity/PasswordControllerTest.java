package com.cassist.identity;

import com.cassist.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordControllerTest {
    final CurrentActor actors=mock(CurrentActor.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final PasswordEncoder encoder=PasswordEncoderFactories.createDelegatingPasswordEncoder();
    final PasswordSessions sessions=mock(PasswordSessions.class);
    final PasswordController controller=new PasswordController(actors,jdbc,encoder,sessions);
    @BeforeEach void setup(){
        when(actors.require()).thenReturn(new Actor("self",Role.STUDENT));
        when(jdbc.queryForList(anyString(),eq(String.class),eq("self"))).thenReturn(List.of(encoder.encode("OldPassword123")));
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.clearSynchronization();}
    @Test void wrongOldPasswordCannotChangeAnything(){
        var e=assertThrows(BusinessException.class,()->controller.change(new PasswordController.Change("wrong","NewPassword456")));
        assertEquals("INVALID_CURRENT_PASSWORD",e.code());
        verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(sessions);
    }
    @Test void changeHashesPasswordAndRevokesOnlyAfterCommit(){
        controller.change(new PasswordController.Change("OldPassword123","NewPassword456"));
        verify(jdbc).update(contains("password_hash=?"),argThat((String hash)->encoder.matches("NewPassword456",hash)),eq("self"));
        verifyNoInteractions(sessions);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
        verify(sessions).invalidate("self");
    }
    @Test void nonAdminCannotResetOthers(){
        assertThrows(BusinessException.class,()->controller.reset("other",new PasswordController.Reset("NewPassword456")));
        verifyNoInteractions(jdbc,sessions);
    }
    @Test void weakAndOversizedPasswordsRejected(){
        for(String p:List.of("short1","onlyletters","123456789","密码A1".repeat(20)))
            assertThrows(BusinessException.class,()->controller.change(new PasswordController.Change("OldPassword123",p)));
        verifyNoInteractions(jdbc,sessions);
    }
    @Test void adminCanResetWithoutKnowingOldPassword(){
        when(actors.require()).thenReturn(new Actor("admin",Role.ADMIN));
        when(jdbc.queryForList(anyString(),eq(String.class),eq("other"))).thenReturn(List.of("other"));
        controller.reset("other",new PasswordController.Reset("NewPassword456"));
        verify(jdbc).update(contains("password_hash=?"),argThat((String hash)->encoder.matches("NewPassword456",hash)),eq("other"));
    }
    @Test void missingAccountCannotBeReset(){
        when(actors.require()).thenReturn(new Actor("admin",Role.ADMIN));
        when(jdbc.queryForList(anyString(),eq(String.class),eq("missing"))).thenReturn(List.of());
        assertThrows(BusinessException.class,()->controller.reset("missing",new PasswordController.Reset("NewPassword456")));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void sessionRevocationTargetsAllSessionsOfOnlyAffectedAccount(){
        var registry=new PasswordSessions();
        var a=session(registry,"self");var b=session(registry,"self");var c=session(registry,"other");
        registry.invalidate("self");assertTrue(a.isInvalid());assertTrue(b.isInvalid());assertFalse(c.isInvalid());
    }
    private MockHttpSession session(PasswordSessions registry,String id){
        var session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new Actor(id,Role.STUDENT),null,List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        registry.sessionCreated(new HttpSessionEvent(session));return session;
    }
}
