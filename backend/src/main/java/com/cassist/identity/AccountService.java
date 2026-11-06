package com.cassist.identity;

import com.cassist.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc; this.passwords = passwords;
        this.dummyHash = passwords.encode("not-a-real-account-password");
    }

    public Actor authenticate(String username, String password) {
        var rows = jdbc.query("SELECT id, username, password_hash, role, enabled FROM app_user WHERE username = ?",
                (rs, n) -> new Account(rs.getString("id"), rs.getString("username"), rs.getString("password_hash"),
                        Role.valueOf(rs.getString("role")), rs.getBoolean("enabled")), username);
        Account account = rows.isEmpty() ? null : rows.getFirst();
        boolean matches = passwords.matches(password, account == null ? dummyHash : account.passwordHash());
        if (!matches || account == null || !account.enabled())
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        return new Actor(account.id(), account.role());
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var rows = jdbc.query("SELECT username, password_hash, role, enabled FROM app_user WHERE username = ?",
                (rs, n) -> User.withUsername(rs.getString("username")).password(rs.getString("password_hash"))
                        .roles(rs.getString("role")).disabled(!rs.getBoolean("enabled")).build(), username);
        if (rows.isEmpty()) throw new UsernameNotFoundException("Account not found");
        return rows.getFirst();
    }

    private record Account(String id, String username, String passwordHash, Role role, boolean enabled) { }
}
