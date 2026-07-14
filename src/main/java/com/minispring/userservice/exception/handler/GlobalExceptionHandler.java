package com.minispring.userservice.exception.handler;

import com.minispring.userservice.exception.BadRequestException;
import com.minispring.userservice.exception.ErrorResponse;
import com.minispring.userservice.exception.ResourceAlreadyExistsException;
import com.minispring.userservice.exception.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExistsException(
            ResourceAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Resource already exists on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "RESOURCE_ALREADY_EXISTS",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler({BadRequestException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBadRequestExceptions(RuntimeException ex, HttpServletRequest request) {
        log.warn("Bad request / Illegal state on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", ex.getMessage(), request.getRequestURI(), getTraceId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not supported on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "METHOD_NOT_ALLOWED",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic locking failure on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                "The data has been modified by another transaction.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Media type not supported on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "UNSUPPORTED_MEDIA_TYPE",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("Field '%s': %s", error.getField(), error.getDefaultMessage()))
                .toList();

        log.warn("DTO validation error on [{}]: {}", request.getRequestURI(), details);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request parameters failed validation.",
                request.getRequestURI(),
                getTraceId(),
                details);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    String field = path.substring(path.lastIndexOf('.') + 1);
                    return String.format("Parameter '%s': %s", field, violation.getMessage());
                })
                .toList();

        log.warn("Param validation error on [{}]: {}", request.getRequestURI(), details);

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request parameters failed validation.",
                request.getRequestURI(),
                getTraceId(),
                details);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String parameterName = ex.getName();
        Object rejectedValue = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();

        log.warn(
                "Type mismatch on [{}]. Param: '{}', Rejected: '{}'",
                request.getRequestURI(),
                parameterName,
                rejectedValue);

        String message = String.format("Invalid parameter format '%s'.", parameterName);
        List<String> details = null;

        if (requiredType != null) {
            if (requiredType.isEnum()) {
                message = String.format("Invalid value '%s' for the parameter '%s'.", rejectedValue, parameterName);
                details = Arrays.stream(requiredType.getEnumConstants())
                        .map(Object::toString)
                        .toList();
            } else if (requiredType.equals(java.util.UUID.class)) {
                message = String.format("The value '%s' is not a valid UUID.", rejectedValue);
                details = List.of("Expected format: 123e4567-e89b-12d3-a456-426614174000");
            }
        }

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PARAMETER_FORMAT",
                message,
                request.getRequestURI(),
                getTraceId(),
                details);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing parameter on [{}]: {}", request.getRequestURI(), ex.getParameterName());

        String message = String.format("The required parameter '%s' is missing.", ex.getParameterName());
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "MISSING_PARAMETER", message, request.getRequestURI(), getTraceId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed JSON request on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_JSON_REQUEST",
                "The request body is missing or contains invalid JSON structure.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication exception on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Full authentication is required to access this resource.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on [{}]: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "You do not have permission to perform this operation.",
                request.getRequestURI(),
                getTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGrpcException(StatusRuntimeException ex, HttpServletRequest request) {
        Status status = ex.getStatus();
        log.error(
                "gRPC downstream exception on [{}]. Status: {}, Description: {}",
                request.getRequestURI(),
                status.getCode(),
                status.getDescription());

        HttpStatus httpStatus =
                switch (status.getCode()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                    case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                    case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                    case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                    default -> HttpStatus.INTERNAL_SERVER_ERROR;
                };

        String message = status.getDescription() != null ? status.getDescription() : "External Auth Service Error";

        ErrorResponse response = ErrorResponse.of(
                httpStatus.value(), status.getCode().name(), message, request.getRequestURI(), getTraceId());

        return ResponseEntity.status(httpStatus).body(response);
    }

    private String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
