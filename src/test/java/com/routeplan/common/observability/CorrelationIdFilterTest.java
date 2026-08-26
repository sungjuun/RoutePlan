package com.routeplan.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void reusesSafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trips/1");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "mobile-client_2026.08-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isEqualTo("mobile-client_2026.08-01");
        assertThat(CorrelationIdFilter.from(request)).isEqualTo("mobile-client_2026.08-01");
    }

    @Test
    void replacesUnsafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/trips/1");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "line break\nnot allowed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generated).isNotBlank();
        assertThatCodeIsUuid(generated);
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
