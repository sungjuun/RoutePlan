package com.routeplan.auth;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSecurityService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthMailQueue mail;

    public AccountSecurityService(JdbcTemplate jdbc, PasswordEncoder passwords, AuthMailQueue mail) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.mail = mail;
    }

    public boolean emailVerified(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, userId));
    }

    @Transactional
    public void verifyEmail(String token) {
        Account account = consume(token, "VERIFY_EMAIL");
        jdbc.update("UPDATE users SET email_verified_at = COALESCE(email_verified_at, now()) WHERE id = ?", account.id());
        jdbc.update("DELETE FROM auth_tokens WHERE user_id = ? AND purpose = 'VERIFY_EMAIL'", account.id());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        AuthService.validatePassword(newPassword);
        Account account = consume(token, "RESET_PASSWORD");
        replacePassword(account, newPassword);
    }

    @Transactional
    public void changePassword(RoutePlanPrincipal principal, String currentPassword, String newPassword) {
        AuthService.validatePassword(newPassword);
        Account account = lockAccount(principal.userId());
        if (account.version() != principal.securityVersion()
                || !passwords.matches(currentPassword, account.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        replacePassword(account, newPassword);
    }

    private void replacePassword(Account account, String newPassword) {
        if (passwords.matches(newPassword, account.passwordHash())) {
            throw new IllegalArgumentException("현재 비밀번호와 다른 새 비밀번호를 입력해 주세요.");
        }
        jdbc.update("UPDATE users SET password_hash = ?, security_version = security_version + 1 WHERE id = ?",
                passwords.encode(newPassword), account.id());
        jdbc.update("DELETE FROM auth_tokens WHERE user_id = ?", account.id());
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", account.email());
        // Already queued messages from an older version are discarded by the worker.
        mail.enqueue(account.email(), "PASSWORD_CHANGED");
    }

    private Account consume(String rawToken, String purpose) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        String hash = AuthTokens.hash(rawToken);
        List<Long> ids = jdbc.query("SELECT user_id FROM auth_tokens WHERE token_hash = ? AND purpose = ?",
                (rs, row) -> rs.getLong(1), hash, purpose);
        if (ids.isEmpty()) throw invalidToken();
        Account account = lockAccount(ids.getFirst());
        // Recheck after acquiring the user lock: concurrent redemptions cannot both succeed.
        int removed = jdbc.update("""
                DELETE FROM auth_tokens WHERE token_hash = ? AND purpose = ?
                    AND security_version = ? AND expires_at > clock_timestamp()
                """, hash, purpose, account.version());
        if (removed != 1) throw invalidToken();
        return account;
    }

    private Account lockAccount(long userId) {
        return jdbc.query("SELECT id, email, password_hash, security_version FROM users WHERE id = ? FOR UPDATE",
                        (rs, row) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4)), userId)
                .stream().findFirst().orElseThrow(AccountSecurityService::invalidToken);
    }

    private static RoutePlanException invalidToken() { return new RoutePlanException(ErrorCode.AUTH_TOKEN_INVALID); }
    private record Account(long id, String email, String passwordHash, long version) { }
}
