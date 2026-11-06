package com.cassist.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CurrentActor currentActor;
    private final AccountService accounts;
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository tokens;
    public AuthController(CurrentActor currentActor, AccountService accounts,
            SecurityContextRepository contexts, HttpSessionCsrfTokenRepository tokens) {
        this.currentActor = currentActor; this.accounts = accounts; this.contexts = contexts; this.tokens = tokens;
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) { return new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken()); }

    @GetMapping("/me")
    public Actor me() { return currentActor.require(); }

    @PostMapping("/login")
    public Actor login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Actor actor = accounts.authenticate(body.username(), body.password());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.role().name())));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        tokens.saveToken(null, request, response);
        return actor;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var logout = new SecurityContextLogoutHandler();
        logout.setSecurityContextRepository(contexts);
        logout.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    public record CsrfView(String headerName, String parameterName, String token) { }
    public record LoginRequest(@NotBlank @Size(max = 100) String username,
                               @NotBlank @Size(max = 200) String password) { }
}
