package com.routeplan.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String INSTANCE_HEADER_NAME = "X-RoutePlan-Instance";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName()
            + ".correlationId";
    private static final Pattern ALLOWED_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Value("${routeplan.instance-id:${HOSTNAME:local}}")
    private String instanceId = "local";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(HEADER_NAME));
        long startedAt = System.nanoTime();
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        response.setHeader(INSTANCE_HEADER_NAME, instanceId);
        MDC.put("correlationId", correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator/")) {
                long elapsedMillis = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
                log.info(
                        "request completed method={} path={} status={} durationMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        elapsedMillis
                );
            }
            MDC.remove("correlationId");
        }
    }

    public static String from(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof String correlationId ? correlationId : null;
    }

    private String resolveCorrelationId(String candidate) {
        if (candidate != null && ALLOWED_VALUE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
