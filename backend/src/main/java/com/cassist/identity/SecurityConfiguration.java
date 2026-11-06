package com.cassist.identity;

import com.cassist.common.ApiError;
import com.cassist.common.TraceIdFilter;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            SecurityContextRepository contexts, HttpSessionCsrfTokenRepository tokens) throws Exception {
        return http.csrf(csrf -> csrf.csrfTokenRepository(tokens))
                .securityContext(context -> context.securityContextRepository(contexts))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll().anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), new ApiError("UNAUTHENTICATED", "请先登录",
                                    (String) request.getAttribute(TraceIdFilter.ATTRIBUTE), Map.of()));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), new ApiError("FORBIDDEN", "无权限或请求校验失败",
                                    (String) request.getAttribute(TraceIdFilter.ATTRIBUTE), Map.of()));
                        }))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
}
