package com.cassist.identity;

import com.cassist.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class PasswordController {
    private final CurrentActor actors;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PasswordSessions sessions;
    public PasswordController(CurrentActor actors, JdbcTemplate jdbc, PasswordEncoder encoder, PasswordSessions sessions) {
        this.actors=actors; this.jdbc=jdbc; this.encoder=encoder; this.sessions=sessions;
    }
    public record Change(String currentPassword, String newPassword) {
        @Override public String toString() { return "Change[credentials omitted]"; }
    }
    public record Reset(String newPassword) {
        @Override public String toString() { return "Reset[credentials omitted]"; }
    }
    @PostMapping("/api/auth/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void change(@RequestBody Change input) {
        Actor actor=actors.require();
        UserManagementService.validatePassword(input.newPassword());
        if(input.currentPassword()==null || input.currentPassword().length()>200)
            UserManagementService.invalid("请输入当前密码");
        var hashes=jdbc.queryForList("SELECT password_hash FROM app_user WHERE id=? AND enabled=TRUE FOR UPDATE",String.class,actor.id());
        if(hashes.isEmpty()) throw new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","请先登录");
        if(!encoder.matches(input.currentPassword(),hashes.getFirst()))
            throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_CURRENT_PASSWORD","当前密码不正确");
        update(actor.id(),input.newPassword());
    }
    @PostMapping("/api/admin/users/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void reset(@PathVariable String id, @RequestBody Reset input) {
        UserManagementService.requireAdmin(actors.require());
        UserManagementService.validatePassword(input.newPassword());
        if(jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",String.class,id).isEmpty())
            AccessPolicy.notFound();
        update(id,input.newPassword());
    }
    private void update(String id,String password) {
        jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?",encoder.encode(password),id);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { sessions.invalidate(id); }
            });
    }
}
