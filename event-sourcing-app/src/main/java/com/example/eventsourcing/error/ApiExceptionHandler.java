package com.example.eventsourcing.error;

import eventsourcing.postgresql.error.AggregateStateException;
import eventsourcing.postgresql.error.CommandValidationException;
import eventsourcing.postgresql.error.IdempotencyConflictException;
import eventsourcing.postgresql.error.IdempotencyInProgressException;
import eventsourcing.postgresql.error.IdempotencyPayloadMismatchException;
import eventsourcing.postgresql.error.OptimisticConcurrencyControlException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

/**
 * Maps the exceptions of the application to the errors of the
 * <a href="https://www.rfc-editor.org/rfc/rfc9457.html">RFC 9457</a> catalog.
 *
 * <p>Each handler only declares which error of the catalog it answers with; the
 * body of the response is built by {@link ProblemDetails}, and the status code is
 * declared by the {@link ErrorType} of the error.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetails problemDetails;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation error");
        return problem(ErrorType.VALIDATION_ERROR, errorMessage, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return problem(ErrorType.INVALID_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(CommandValidationException.class)
    protected ResponseEntity<Object> handleCommandValidation(CommandValidationException ex, WebRequest request) {
        return problem(ErrorType.VALIDATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(AggregateStateException.class)
    protected ResponseEntity<Object> handleAggregateState(AggregateStateException ex, WebRequest request) {
        return problem(ErrorType.ORDER_STATE_CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(OptimisticConcurrencyControlException.class)
    protected ResponseEntity<Object> handleOptimisticConcurrency(
            OptimisticConcurrencyControlException ex, WebRequest request) {
        return problem(ErrorType.CONCURRENT_MODIFICATION, ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    protected ResponseEntity<Object> handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {
        return problem(ErrorType.IDEMPOTENCY_CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    protected ResponseEntity<Object> handleInProgress(IdempotencyInProgressException ex, WebRequest request) {
        ResponseEntity<Object> response = problem(
                ErrorType.IDEMPOTENCY_REQUEST_IN_PROGRESS, ex.getMessage(), request);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        return response;
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    protected ResponseEntity<Object> handlePayloadMismatch(IdempotencyPayloadMismatchException ex, WebRequest request) {
        return problem(ErrorType.IDEMPOTENCY_KEY_MISMATCH, ex.getMessage(), request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    protected ResponseEntity<Object> handleNotFound(NoSuchElementException ex, WebRequest request) {
        return problem(ErrorType.RESOURCE_NOT_FOUND, ex.getMessage(), request);
    }

    /**
     * Adds the {@code code} member to the problem details of the errors that are
     * raised by the framework itself, so that the member is always present.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail detail) {
            var properties = detail.getProperties();
            if (properties == null || !properties.containsKey("code")) {
                detail.setProperty("code", ErrorType.codeFor(status));
            }
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    private ResponseEntity<Object> problem(ErrorType type, String detail, WebRequest request) {
        return problemDetails.of(type, detail, pathOf(request));
    }

    private String pathOf(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring("uri=".length()) : description;
    }
}
