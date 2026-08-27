package com.routeplan.common.api;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.common.observability.CorrelationIdFilter;
import com.routeplan.optimization.constraint.InfeasibleScheduleException;
import com.routeplan.optimization.constraint.InfeasibleReturnException;
import com.routeplan.optimization.constraint.BudgetConstraintException;
import com.routeplan.integration.google.ExternalProviderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BudgetConstraintException.class)
    ResponseEntity<ErrorResponse> handleBudgetConstraint(
            BudgetConstraintException exception, HttpServletRequest request
    ) {
        ErrorCode code = exception.reason() == BudgetConstraintException.Reason.MISSING_COST
                ? ErrorCode.COST_ESTIMATES_REQUIRED : ErrorCode.INFEASIBLE_BUDGET;
        return response(code, exception.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return response(
                ErrorCode.AUTHENTICATION_FAILED,
                ErrorCode.AUTHENTICATION_FAILED.message(),
                request
        );
    }

    @ExceptionHandler(RoutePlanException.class)
    ResponseEntity<ErrorResponse> handleRoutePlanException(
            RoutePlanException exception,
            HttpServletRequest request
    ) {
        return response(exception.errorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(InfeasibleReturnException.class)
    ResponseEntity<ErrorResponse> handleInfeasibleReturn(
            InfeasibleReturnException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.INFEASIBLE_RETURN, exception.getMessage(), request);
    }

    @ExceptionHandler(InfeasibleScheduleException.class)
    ResponseEntity<ErrorResponse> handleInfeasibleSchedule(
            InfeasibleScheduleException exception,
            HttpServletRequest request
    ) {
        List<ErrorResponse.Violation> violations = exception.violations().stream()
                .map(violation -> new ErrorResponse.Violation(
                        violation.placeId(),
                        violation.placeName(),
                        violation.reason().name(),
                        violation.message()
                ))
                .toList();
        return response(
                ErrorCode.INFEASIBLE_MUST_VISIT,
                exception.getMessage(),
                request,
                violations
        );
    }

    @ExceptionHandler(ExternalProviderException.class)
    ResponseEntity<ErrorResponse> handleExternalProvider(
            ExternalProviderException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = switch (exception.failure()) {
            case NOT_CONFIGURED -> ErrorCode.EXTERNAL_PROVIDER_NOT_CONFIGURED;
            case RATE_LIMITED -> ErrorCode.EXTERNAL_PROVIDER_RATE_LIMITED;
            case UNAVAILABLE -> ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE;
            case INVALID_RESPONSE -> ErrorCode.EXTERNAL_PROVIDER_INVALID_RESPONSE;
            case ROUTE_NOT_FOUND -> ErrorCode.ROUTE_NOT_FOUND;
        };
        return response(errorCode, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(", "));
        return response(ErrorCode.INVALID_INPUT, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.INVALID_INPUT, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.INVALID_INPUT, exception.getMessage(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> handleUnreadableInput(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.message(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.CONFLICT, ErrorCode.CONFLICT.message(), request);
    }

    private ResponseEntity<ErrorResponse> response(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return response(errorCode, message, request, List.of());
    }

    private ResponseEntity<ErrorResponse> response(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<ErrorResponse.Violation> violations
    ) {
        ErrorResponse body = new ErrorResponse(
                errorCode.name(),
                message,
                request.getRequestURI(),
                CorrelationIdFilter.from(request),
                Instant.now(),
                violations
        );
        return ResponseEntity.status(errorCode.status()).body(body);
    }
}
