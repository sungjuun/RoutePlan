package com.routeplan.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.Instant;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Keeps API integration tests concise while exercising Spring Security and CSRF filters.
 */
public final class AuthenticatedMockMvc {

    private final MockMvc delegate;
    private Long userId;

    public AuthenticatedMockMvc(MockMvc delegate) {
        this.delegate = delegate;
    }

    public void authenticate(long userId) {
        this.userId = userId;
    }

    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        request.with(csrf());
        if (userId != null) {
            RoutePlanPrincipal principal = new RoutePlanPrincipal(
                    userId,
                    "integration-" + userId + "@routeplan.test",
                    "integration-" + userId,
                    "{noop}unused",
                    Instant.now()
            );
            request.with(user(principal));
        }
        return delegate.perform(request);
    }
}
