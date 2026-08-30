package com.routeplan.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final List<IpAddressMatcher> trustedProxies;

    public AuthRateLimiter(JdbcTemplate jdbc,
            @Value("${routeplan.auth.trusted-proxies:}") String trustedProxies) {
        this.jdbc = jdbc;
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::strip).filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }

    public String clientAddress(HttpServletRequest request) {
        // Never trust a client-supplied forwarding header without an explicit proxy allowlist.
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded != null && forwarded.matches("[0-9a-fA-F:.]{2,45}")
                && trustedProxies.stream().anyMatch(proxy -> proxy.matches(request.getRemoteAddr()))) {
            return forwarded;
        }
        return request.getRemoteAddr();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = AuthRateLimitException.class)
    public void require(String scope, String identity, int limit, Duration window) {
        long retryAfter = consume(scope, identity, limit, window);
        if (retryAfter > 0) throw new AuthRateLimitException(retryAfter);
    }

    // Each upsert commits independently: rejecting the request must not roll back its counter.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long consume(String scope, String identity, int limit, Duration window) {
        return jdbc.queryForObject("""
                INSERT INTO auth_rate_limits(bucket_key, attempts, resets_at)
                VALUES (?, 1, clock_timestamp() + (? * interval '1 second'))
                ON CONFLICT (bucket_key) DO UPDATE SET
                  attempts = CASE WHEN auth_rate_limits.resets_at <= clock_timestamp()
                                  THEN 1 ELSE LEAST(auth_rate_limits.attempts + 1, ?) END,
                  resets_at = CASE WHEN auth_rate_limits.resets_at <= clock_timestamp()
                                   THEN excluded.resets_at ELSE auth_rate_limits.resets_at END
                RETURNING CASE WHEN attempts > ?
                    THEN GREATEST(1, CEIL(EXTRACT(EPOCH FROM (resets_at - clock_timestamp()))))::bigint
                    ELSE 0 END
                """, Long.class, AuthTokens.hash(scope + ":" + identity), window.toSeconds(), limit + 1, limit);
    }
}
