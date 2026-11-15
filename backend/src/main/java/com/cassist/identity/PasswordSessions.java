package com.cassist.identity;

import jakarta.servlet.http.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class PasswordSessions implements HttpSessionListener {
    private final Set<HttpSession> sessions=ConcurrentHashMap.newKeySet();
    @Bean
    ServletListenerRegistrationBean<PasswordSessions> passwordSessionListener() {
        return new ServletListenerRegistrationBean<>(this);
    }
    @Override public void sessionCreated(HttpSessionEvent event) { sessions.add(event.getSession()); }
    @Override public void sessionDestroyed(HttpSessionEvent event) { sessions.remove(event.getSession()); }
    public void invalidate(String userId) {
        for(HttpSession session:sessions) {
            try {
                Object value=session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if(value instanceof SecurityContext context && context.getAuthentication()!=null
                    && context.getAuthentication().getPrincipal() instanceof Actor actor && actor.id().equals(userId))
                    session.invalidate();
            } catch(IllegalStateException ignored) { sessions.remove(session); }
        }
    }
}
