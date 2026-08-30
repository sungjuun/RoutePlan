package com.routeplan.auth;

import com.routeplan.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorWriter errorWriter,
            SecurityContextRepository securityContextRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbc
    ) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/auth/me", "/api/v1/auth/options")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login",
                                "/api/v1/auth/email/verify", "/api/v1/auth/password/reset-request", "/api/v1/auth/password/reset")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/routes", "/api/v1/routes/**")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .deleteCookies("ROUTEPLAN_SESSION")
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(ErrorCode.AUTHENTICATION_REQUIRED, request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(ErrorCode.ACCESS_DENIED, request, response))
                );
        http.addFilterAfter(new SessionValidityFilter(jdbc),
                org.springframework.security.web.context.SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    org.springframework.session.web.http.CookieSerializer sessionCookieSerializer(
            @org.springframework.beans.factory.annotation.Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        var serializer = new org.springframework.session.web.http.DefaultCookieSerializer();
        serializer.setCookieName("ROUTEPLAN_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy(
                java.util.List.of(new ChangeSessionIdAuthenticationStrategy(),
                        new org.springframework.security.web.csrf.CsrfAuthenticationStrategy(
                                new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository())));
    }
}
