package com.routeplan.auth;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.time.Instant;
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

    public AccountSnapshot snapshot(long userId) {
        return jdbc.query("""
                        SELECT id, email, nickname, created_at, email_verified_at IS NOT NULL, security_version
                        FROM users WHERE id = ?
                        """, (rs, row) -> new AccountSnapshot(
                        rs.getLong("id"), rs.getString("email"), rs.getString("nickname"),
                        rs.getTimestamp("created_at").toInstant(), rs.getBoolean(5), rs.getLong("security_version")),
                        userId)
                .stream().findFirst().orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public AccountSnapshot changeNickname(RoutePlanPrincipal principal, String rawNickname) {
        String nickname = normalizeNickname(rawNickname);
        Account account = lockAccount(principal.userId());
        requireCurrentVersion(principal, account);
        if (nickname.equals(account.nickname())) return snapshot(account.id());
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE nickname = ? AND id <> ?)",
                Boolean.class, nickname, account.id()))) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_NICKNAME);
        }
        jdbc.update("UPDATE users SET nickname = ? WHERE id = ?", nickname, account.id());
        return snapshot(account.id());
    }

    @Transactional
    public void changeEmail(RoutePlanPrincipal principal, String currentPassword, String rawEmail) {
        String email = AuthService.normalizeEmail(rawEmail);
        Account account = lockAccount(principal.userId());
        requireCurrentPassword(principal, account, currentPassword);
        if (email.equals(account.email())) {
            throw new IllegalArgumentException("현재 이메일과 다른 이메일을 입력해 주세요.");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE lower(email) = lower(?) AND id <> ?)",
                Boolean.class, email, account.id()))) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_EMAIL);
        }
        jdbc.update("""
                UPDATE users SET email = ?, email_verified_at = NULL,
                    security_version = security_version + 1 WHERE id = ?
                """, email, account.id());
        revokeAccountAccess(account);
        mail.enqueue(email, "VERIFY_EMAIL");
    }

    @Transactional
    public void deleteAccount(RoutePlanPrincipal principal, String currentPassword, String confirmation) {
        if (!"회원 탈퇴".equals(confirmation == null ? "" : confirmation.strip())) {
            throw new IllegalArgumentException("확인 문구에 ‘회원 탈퇴’를 정확히 입력해 주세요.");
        }
        Account account = lockAccount(principal.userId());
        requireCurrentPassword(principal, account, currentPassword);
        revokeAccountAccess(account);
        int removed = jdbc.update("DELETE FROM users WHERE id = ?", account.id());
        if (removed != 1) throw new RoutePlanException(ErrorCode.USER_NOT_FOUND);
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
        requireCurrentPassword(principal, account, currentPassword);
        replacePassword(account, newPassword);
    }

    private void replacePassword(Account account, String newPassword) {
        if (passwords.matches(newPassword, account.passwordHash())) {
            throw new IllegalArgumentException("현재 비밀번호와 다른 새 비밀번호를 입력해 주세요.");
        }
        jdbc.update("UPDATE users SET password_hash = ?, security_version = security_version + 1 WHERE id = ?",
                passwords.encode(newPassword), account.id());
        revokeAccountAccess(account);
        // Already queued messages from an older version are discarded by the worker.
        mail.enqueue(account.email(), "PASSWORD_CHANGED");
    }

    private void revokeAccountAccess(Account account) {
        jdbc.update("DELETE FROM auth_tokens WHERE user_id = ?", account.id());
        jdbc.update("DELETE FROM auth_mail_jobs WHERE user_id = ?", account.id());
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", account.email());
    }

    private void requireCurrentPassword(RoutePlanPrincipal principal, Account account, String currentPassword) {
        requireCurrentVersion(principal, account);
        if (!passwords.matches(currentPassword == null ? "" : currentPassword, account.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    private static void requireCurrentVersion(RoutePlanPrincipal principal, Account account) {
        if (account.version() != principal.securityVersion()) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    private static String normalizeNickname(String rawNickname) {
        String nickname = rawNickname == null ? "" : rawNickname.strip();
        if (nickname.length() < 2 || nickname.length() > 50) {
            throw new IllegalArgumentException("닉네임은 공백을 제외하고 2자 이상 50자 이하여야 합니다.");
        }
        return nickname;
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
        return jdbc.query("SELECT id, email, nickname, password_hash, security_version FROM users WHERE id = ? FOR UPDATE",
                        (rs, row) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)), userId)
                .stream().findFirst().orElseThrow(AccountSecurityService::invalidToken);
    }

    private static RoutePlanException invalidToken() { return new RoutePlanException(ErrorCode.AUTH_TOKEN_INVALID); }
    public record AccountSnapshot(long id, String email, String nickname, Instant createdAt,
                                  boolean emailVerified, long securityVersion) { }
    private record Account(long id, String email, String nickname, String passwordHash, long version) { }
}
