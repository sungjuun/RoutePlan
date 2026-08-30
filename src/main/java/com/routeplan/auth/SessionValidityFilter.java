package com.routeplan.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionValidityFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    SessionValidityFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof RoutePlanPrincipal principal) {
            var versions = jdbc.query("SELECT security_version FROM users WHERE id = ?",
                    (rs, row) -> rs.getLong(1), principal.userId());
            if (versions.isEmpty() || versions.getFirst() != principal.securityVersion()) {
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
