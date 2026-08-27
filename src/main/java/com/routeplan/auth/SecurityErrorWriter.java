package com.routeplan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.common.api.ErrorResponse;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public SecurityErrorWriter() {
    }

    public void write(
            ErrorCode errorCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                errorCode.name(),
                errorCode.message(),
                request.getRequestURI(),
                CorrelationIdFilter.from(request),
                Instant.now(),
                List.of()
        ));
    }
}
