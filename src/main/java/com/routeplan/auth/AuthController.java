package com.routeplan.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.Duration;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final com.routeplan.user.application.ProfileImageService images;
    private final AccountSecurityService accountSecurity;
    private final AuthRateLimiter limits;
    private final AuthMailQueue mail;
    private final AuthMailSettings mailSettings;

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            com.routeplan.user.application.ProfileImageService images,
            AccountSecurityService accountSecurity,
            AuthRateLimiter limits,
            AuthMailQueue mail,
            AuthMailSettings mailSettings
    ) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.images = images;
        this.accountSecurity = accountSecurity;
        this.limits = limits;
        this.mail = mail;
        this.mailSettings = mailSettings;
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/options")
    public AuthOptions options() { return new AuthOptions(mailSettings.mode()); }

    @PostMapping("/email/verification-request")
    public ResponseEntity<Void> requestVerification(@AuthenticationPrincipal RoutePlanPrincipal principal) {
        requireMail();
        limits.require("verify-user", principal.userId().toString(), 1, Duration.ofMinutes(1));
        mail.enqueue(principal.email(), "VERIFY_EMAIL");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/verify")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        limitRedemption(request);
        accountSecurity.verifyEmail(body.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset-request")
    public ResponseEntity<MessageView> requestPasswordReset(@Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        requireMail();
        limits.require("reset-ip", limits.clientAddress(request), 20, Duration.ofMinutes(15));
        String email = body.email().strip().toLowerCase(Locale.ROOT);
        if (limits.consume("reset-email", email, 3, Duration.ofMinutes(15)) == 0) {
            mail.enqueue(email, "RESET_PASSWORD");
        }
        return ResponseEntity.accepted().body(new MessageView("가입된 이메일이라면 비밀번호 재설정 메일을 보냈습니다. 메일함을 확인해 주세요."));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        limitRedemption(request);
        accountSecurity.resetPassword(body.token(), body.newPassword());
        clearSession(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        limits.require("change-user", principal.userId().toString(), 5, Duration.ofMinutes(15));
        accountSecurity.changePassword(principal, body.currentPassword(), body.newPassword());
        clearSession(request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/profile")
    public AuthUserView changeNickname(@AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ChangeNicknameRequest body) {
        limits.require("nickname-user", principal.userId().toString(), 10, Duration.ofHours(1));
        return userView(accountSecurity.changeNickname(principal, body.nickname()));
    }

    @PostMapping("/email/change")
    public ResponseEntity<Void> changeEmail(@AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody ChangeEmailRequest body, HttpServletRequest request) {
        requireMail();
        limits.require("email-change-user", principal.userId().toString(), 5, Duration.ofDays(1));
        accountSecurity.changeEmail(principal, body.currentPassword(), body.newEmail());
        clearSession(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal RoutePlanPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest body, HttpServletRequest request) {
        limits.require("account-delete-user", principal.userId().toString(), 3, Duration.ofDays(1));
        accountSecurity.deleteAccount(principal, body.currentPassword(), body.confirmation());
        clearSession(request);
        return ResponseEntity.noContent().build();
    }

    private void limitRedemption(HttpServletRequest request) {
        limits.require("token-ip", limits.clientAddress(request), 30, Duration.ofMinutes(15));
    }

    private void requireMail() {
        if (!mailSettings.enabled()) throw new com.routeplan.common.error.RoutePlanException(
                com.routeplan.common.error.ErrorCode.AUTH_MAIL_DISABLED);
    }

    private void clearSession(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    public AuthSessionView me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof RoutePlanPrincipal principal)) {
            return new AuthSessionView(false, null);
        }
        return new AuthSessionView(true, userView(accountSecurity.snapshot(principal.userId())));
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthSessionView> signup(
            @Valid @RequestBody SignupRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        limits.require("signup-ip", limits.clientAddress(request), 20, Duration.ofHours(1));
        authService.register(body.email(), body.nickname(), body.password());
        Authentication authentication = authenticate(
                body.email(), body.password(), request, response
        );
        return ResponseEntity.status(201).body(session(authentication));
    }

    @PostMapping("/login")
    public AuthSessionView login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        limits.require("login-ip", limits.clientAddress(request), 100, Duration.ofMinutes(15));
        limits.require("login-email", body.email().strip().toLowerCase(Locale.ROOT), 10, Duration.ofMinutes(15));
        return session(authenticate(body.email(), body.password(), request, response));
    }

    private Authentication authenticate(
            String email,
            String password,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email.strip(), password)
        );
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return authentication;
    }

    private AuthSessionView session(Authentication authentication) {
        RoutePlanPrincipal principal = (RoutePlanPrincipal) authentication.getPrincipal();
        return new AuthSessionView(true, userView(accountSecurity.snapshot(principal.userId())));
    }

    private AuthUserView userView(AccountSecurityService.AccountSnapshot account) {
        return new AuthUserView(account.id(), account.email(), account.nickname(),
                account.createdAt(), images.url(account.id()), account.emailVerified());
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 2, max = 50) String nickname,
            @NotBlank @Size(min = 10, max = 72) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record CsrfView(String headerName, String token) {
    }

    public record AuthSessionView(boolean authenticated, AuthUserView user) {
    }

    public record AuthUserView(Long id, String email, String nickname, Instant createdAt, String profileImageUrl, boolean emailVerified) {}
    public record AuthOptions(AuthMailSettings.Mode mailMode) { }
    public record MessageView(String message) { }
    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) { }
    public record TokenRequest(@NotBlank @Size(max = 128) String token) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = 10, max = 72) String newPassword) { }
    public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword) { }
    public record ChangeNicknameRequest(@NotBlank @Size(min = 2, max = 50) String nickname) { }
    public record ChangeEmailRequest(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Email @Size(max = 254) String newEmail) { }
    public record DeleteAccountRequest(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(max = 20) String confirmation) { }
}
