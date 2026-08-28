package com.routeplan.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            com.routeplan.user.application.ProfileImageService images
    ) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.images = images;
    }

    @GetMapping("/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/me")
    public AuthSessionView me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof RoutePlanPrincipal principal)) {
            return new AuthSessionView(false, null);
        }
        return new AuthSessionView(true, userView(principal));
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthSessionView> signup(
            @Valid @RequestBody SignupRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
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
        return new AuthSessionView(true, userView(principal));
    }

    private AuthUserView userView(RoutePlanPrincipal principal) {
        return new AuthUserView(principal.userId(), principal.email(), principal.nickname(),
                principal.createdAt(), images.url(principal.userId()));
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

    public record AuthUserView(Long id, String email, String nickname, Instant createdAt, String profileImageUrl) {}
}
