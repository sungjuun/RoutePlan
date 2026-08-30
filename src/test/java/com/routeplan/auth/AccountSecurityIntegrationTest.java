package com.routeplan.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.routeplan.common.error.RoutePlanException;
import com.routeplan.user.persistence.UserRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"routeplan.auth.mail-mode=LOCAL", "routeplan.auth.mail-poll-ms=86400000"})
@AutoConfigureMockMvc
@Testcontainers
class AccountSecurityIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final String PASSWORD = "Account-test-2026!";
    private static final String NEW_PASSWORD = "New-account-2026!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthMailQueue queue;
    @Autowired AccountSecurityService security;
    @Autowired AuthRateLimiter limiter;
    @MockitoBean JavaMailSender mail;

    @BeforeEach
    void cleanEphemeralSecurityState() {
        // This class owns its disposable PostgreSQL container, never the development database.
        jdbc.update("DELETE FROM auth_mail_jobs");
        jdbc.update("DELETE FROM auth_tokens");
        jdbc.update("DELETE FROM auth_rate_limits");
        jdbc.update("DELETE FROM spring_session");
        reset(mail);
    }

    @Test
    void persistsSessionWithoutPasswordCredentialsAndLogsOutPersistently() throws Exception {
        Account account = signup();
        assertThat(account.cookie()).isNotNull();
        assertThat(account.cookie().isHttpOnly()).isTrue();
        assertThat(account.cookie().getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class, account.email())).isEqualTo(1);
        String hash = users.findById(account.id()).orElseThrow().getPasswordHash();
        List<byte[]> attributes = jdbc.query("SELECT attribute_bytes FROM spring_session_attributes",
                (rs, row) -> rs.getBytes(1));
        assertThat(attributes).isNotEmpty();
        for (byte[] bytes : attributes) {
            assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).doesNotContain(PASSWORD, hash);
        }
        mvc.perform(get("/api/v1/auth/me").cookie(account.cookie()))
                .andExpect(jsonPath("$.user.id").value(account.id()))
                .andExpect(jsonPath("$.user.emailVerified").value(false));
        perform("/auth/logout", "{}", account.cookie()).andExpect(status().isNoContent());
        anonymous(account.cookie());
    }

    @Test
    void verifiesUsingOnlyHashedSingleUseTokensWithoutAutomaticGetConsumption() throws Exception {
        Account account = signup();
        String token = deliverAndReadToken("verify-email");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM auth_tokens WHERE user_id = ?", String.class, account.id()))
                .isEqualTo(AuthTokens.hash(token)).isNotEqualTo(token);
        mvc.perform(get("/api/v1/auth/email/verify").param("token", token)).andExpect(status().isUnauthorized());
        assertThat(security.emailVerified(account.id())).isFalse();
        perform("/auth/email/verify", tokenBody(token), null).andExpect(status().isNoContent());
        assertThat(security.emailVerified(account.id())).isTrue();
        mvc.perform(get("/api/v1/auth/me").cookie(account.cookie())).andExpect(jsonPath("$.user.emailVerified").value(true));
        perform("/auth/email/verify", tokenBody(token), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void rejectsExpiredMalformedAndWrongPurposeTokens() throws Exception {
        Account account = signup();
        String token = deliverAndReadToken("verify-email");
        perform("/auth/password/reset", resetBody(token), null).andExpect(status().isBadRequest());
        jdbc.update("UPDATE auth_tokens SET expires_at = now() - interval '1 second' WHERE user_id = ?", account.id());
        perform("/auth/email/verify", tokenBody(token), null).andExpect(status().isBadRequest());
        perform("/auth/email/verify", tokenBody("bad-link"), null).andExpect(status().isBadRequest());
        assertThat(security.emailVerified(account.id())).isFalse();
    }

    @Test
    void resetRequestDoesNotRevealMembershipOrAccountThrottle() throws Exception {
        Account account = signup();
        jdbc.update("DELETE FROM auth_mail_jobs");
        String expected = perform("/auth/password/reset-request", emailBody(account.email()), null)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = "unknown-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 4; i++) {
            perform("/auth/password/reset-request", emailBody(unknown), null)
                    .andExpect(status().isAccepted()).andExpect(content().json(expected));
            perform("/auth/password/reset-request", emailBody(account.email().toUpperCase()), null)
                    .andExpect(status().isAccepted()).andExpect(content().json(expected));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_mail_jobs WHERE user_id = ?", Integer.class, account.id())).isEqualTo(3);
    }

    @Test
    void passwordResetRevokesAllSessionsAndAllOtherTokens() throws Exception {
        Account account = signup();
        Cookie secondSession = login(account.email(), PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("ROUTEPLAN_SESSION");
        perform("/auth/password/reset-request", emailBody(account.email()), null).andExpect(status().isAccepted());
        String token = deliverAndReadToken("reset-password");
        perform("/auth/password/reset", resetBody(token), null).andExpect(status().isNoContent());
        anonymous(account.cookie());
        anonymous(secondSession);
        login(account.email(), PASSWORD).andExpect(status().isUnauthorized());
        login(account.email(), NEW_PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id = ?", Integer.class, account.id())).isZero();
        perform("/auth/password/reset", resetBody(token), null).andExpect(status().isBadRequest());
        assertThat(users.findById(account.id()).orElseThrow().getSecurityVersion()).isEqualTo(1);
        assertThat(queue.processOne()).isTrue();
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).anySatisfy(message -> {
            assertThat(message.getSubject()).contains("비밀번호가 변경");
            assertThat(message.getText()).doesNotContain(PASSWORD, NEW_PASSWORD);
        });
    }

    @Test
    void changeRequiresCurrentPasswordAndRejectsStaleAuthentication() throws Exception {
        Account account = signup();
        Cookie second = login(account.email(), PASSWORD).andReturn().getResponse().getCookie("ROUTEPLAN_SESSION");
        perform("/auth/password/change", changeBody("incorrect-password", NEW_PASSWORD), account.cookie())
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(account.cookie())).andExpect(jsonPath("$.authenticated").value(true));
        perform("/auth/password/change", changeBody(PASSWORD, PASSWORD), account.cookie()).andExpect(status().isBadRequest());
        perform("/auth/password/change", changeBody(PASSWORD, NEW_PASSWORD), account.cookie()).andExpect(status().isNoContent());
        anonymous(account.cookie());
        anonymous(second);
        var stalePrincipal = new RoutePlanPrincipal(account.id(), account.email(), "stale", null, Instant.now(), 0);
        mvc.perform(get("/api/v1/trips").with(user(stalePrincipal))).andExpect(status().isUnauthorized());
    }

    @Test
    void publicRecoveryAndAuthenticatedPasswordChangesKeepCsrfProtection() throws Exception {
        Account account = signup();
        for (String path : List.of("/auth/password/reset-request", "/auth/email/verify", "/auth/password/reset")) {
            mvc.perform(post("/api/v1" + path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/auth/password/change").cookie(account.cookie())
                .contentType(MediaType.APPLICATION_JSON).content(changeBody(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden());
        perform("/auth/email/verification-request", "{}", null).andExpect(status().isUnauthorized());
    }

    @Test
    void loginLimitsSurviveNewLimiterInstanceAndExpire() throws Exception {
        Account account = signup();
        for (int i = 0; i < 10; i++) login(account.email(), "incorrect-password").andExpect(status().isUnauthorized());
        login(account.email().toUpperCase(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.code").value("AUTH_RATE_LIMITED"));
        assertThat(new AuthRateLimiter(jdbc, "").consume("login-email", account.email(), 10, Duration.ofMinutes(15))).isPositive();
        jdbc.update("UPDATE auth_rate_limits SET resets_at = now() - interval '1 second'");
        login(account.email(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void limitsUnknownAccountsAndIpFloodsToo() throws Exception {
        String unknown = UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 10; i++) login(unknown, PASSWORD).andExpect(status().isUnauthorized());
        login(unknown, PASSWORD).andExpect(status().isTooManyRequests());
        for (int i = 0; i < 20; i++) perform("/auth/password/reset-request", emailBody(unknown), null).andExpect(status().isAccepted());
        perform("/auth/password/reset-request", emailBody(unknown), null).andExpect(status().isTooManyRequests());
    }

    @Test
    void limitsVerificationResendsAndLeavesPreviouslyDeliveredLinksUsable() throws Exception {
        Account account = signup();
        String first = deliverAndReadToken("verify-email");
        perform("/auth/email/verification-request", "{}", account.cookie()).andExpect(status().isNoContent());
        String second = deliverAndReadToken("verify-email");
        perform("/auth/email/verification-request", "{}", account.cookie()).andExpect(status().isTooManyRequests());
        perform("/auth/email/verify", tokenBody(first), null).andExpect(status().isNoContent());
        perform("/auth/email/verify", tokenBody(second), null).andExpect(status().isBadRequest());
    }

    @Test
    void passwordValidationFailureDoesNotConsumeResetLink() throws Exception {
        Account account = signup();
        String token = insertToken(account, "RESET_PASSWORD");
        perform("/auth/password/reset", "{\"token\":\"" + token + "\",\"newPassword\":\"" + "가".repeat(25) + "\"}", null)
                .andExpect(status().isBadRequest());
        perform("/auth/password/reset", resetBody(token), null).andExpect(status().isNoContent());
    }

    @Test
    void concurrentRedemptionAllowsOnlyOnePasswordReset() throws Exception {
        Account account = signup();
        String token = insertToken(account, "RESET_PASSWORD");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await(5, TimeUnit.SECONDS);
            try { security.resetPassword(token, NEW_PASSWORD); return true; }
            catch (RoutePlanException exception) { return false; }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void concurrentRateLimitUpdatesCannotExceedBudget() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 20)
                    .<Callable<Long>>mapToObj(i -> () -> limiter.consume("parallel", "test", 5, Duration.ofMinutes(1))).toList();
            var results = executor.invokeAll(tasks);
            int allowed = 0;
            for (var result : results) if (result.get() == 0) allowed++;
            assertThat(allowed).isEqualTo(5);
        }
    }

    @Test
    void smtpFailureRetriesWithoutStoringRawTokensAndOldQueuedLinksAreDropped() throws Exception {
        Account account = signup();
        doThrow(new MailSendException("synthetic delivery failure")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(queue.processOne()).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempts FROM auth_mail_jobs WHERE user_id = ?", Integer.class, account.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens", Integer.class)).isZero();
        assertThat(queue.processOne()).isFalse();
        jdbc.update("UPDATE auth_mail_jobs SET available_at = now()");
        jdbc.update("UPDATE users SET security_version = security_version + 1 WHERE id = ?", account.id());
        reset(mail);
        assertThat(queue.processOne()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_mail_jobs", Integer.class)).isZero();
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    @Test
    void rejectsUntrustedForwardedHeadersAndUnsafePublicUrls() {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Real-IP", "203.0.113.5");
        assertThat(new AuthRateLimiter(jdbc, "").clientAddress(request)).isEqualTo("10.0.0.10");
        assertThat(new AuthRateLimiter(jdbc, "10.0.0.10/32").clientAddress(request)).isEqualTo("203.0.113.5");
        for (String url : List.of("http://example.com", "https://example.com/#other", "https://x@example.com", "https://example.com/path")) {
            assertThatThrownBy(() -> new AuthMailSettings(AuthMailSettings.Mode.SMTP, url, "test@example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Account signup() throws Exception {
        String email = "security-" + UUID.randomUUID() + "@example.com";
        String nickname = "security-" + UUID.randomUUID();
        var response = perform("/auth/signup", "{\"email\":\"" + email + "\",\"nickname\":\"" + nickname
                + "\",\"password\":\"" + PASSWORD + "\"}", null).andExpect(status().isCreated()).andReturn().getResponse();
        return new Account(users.findByEmailIgnoreCase(email).orElseThrow().getId(), email, response.getCookie("ROUTEPLAN_SESSION"));
    }

    private ResultActions perform(String path, String body, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
        if (cookie != null) request.cookie(cookie);
        return mvc.perform(request);
    }

    private ResultActions login(String email, String password) throws Exception {
        return perform("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null);
    }

    private void anonymous(Cookie cookie) throws Exception {
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(jsonPath("$.authenticated").value(false));
    }

    private String deliverAndReadToken(String fragment) {
        reset(mail);
        while (queue.processOne()) { }
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(messages.capture());
        String text = messages.getAllValues().stream().map(SimpleMailMessage::getText)
                .filter(value -> value != null && value.contains("/#" + fragment + "=")).reduce((first, last) -> last).orElseThrow();
        var matcher = java.util.regex.Pattern.compile("/#" + fragment + "=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String insertToken(Account account, String purpose) {
        String token = AuthTokens.create();
        jdbc.update("INSERT INTO auth_tokens(token_hash, user_id, purpose, security_version, expires_at) VALUES (?, ?, ?, 0, ?)",
                AuthTokens.hash(token), account.id(), purpose, Timestamp.from(Instant.now().plusSeconds(1800)));
        return token;
    }

    private String tokenBody(String token) { return "{\"token\":\"" + token + "\"}"; }
    private String emailBody(String email) { return "{\"email\":\"" + email + "\"}"; }
    private String resetBody(String token) { return "{\"token\":\"" + token + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"; }
    private String changeBody(String current, String next) { return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"; }
    private record Account(long id, String email, Cookie cookie) { }
}
