package com.example.eventsourcing.controller;

import eventsourcing.postgresql.error.AggregateStateException;
import eventsourcing.postgresql.error.CommandValidationException;
import eventsourcing.postgresql.error.IdempotencyConflictException;
import eventsourcing.postgresql.error.IdempotencyInProgressException;
import eventsourcing.postgresql.error.IdempotencyPayloadMismatchException;
import eventsourcing.postgresql.error.OptimisticConcurrencyControlException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Renders the errors of the API as RFC 9457 problem details.
 *
 * <p>Every error of the API, whether it is raised by the application or by the
 * framework, is serialized with the same shape and the same media type:
 *
 * <pre>
 * {
 *   "type": "urn:problem-type:order-state-conflict",
 *   "title": "Invalid order state",
 *   "status": 409,
 *   "detail": "Order in status COMPLETED can't be cancelled",
 *   "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
 *   "code": "ORDER_STATE_CONFLICT"
 * }
 * </pre>
 *
 * <p>The {@code code} member is an extension of the specification. It is a
 * stable, machine-readable identifier that clients can branch on, whereas the
 * {@code detail} member is a human-readable message that may change.
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation error");
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", errorMessage, request, "VALIDATION_ERROR");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), request, "INVALID_REQUEST");
    }

    @ExceptionHandler(CommandValidationException.class)
    protected ResponseEntity<Object> handleCommandValidation(CommandValidationException ex, WebRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed", ex.getMessage(), request, "VALIDATION_FAILED");
    }

    @ExceptionHandler(AggregateStateException.class)
    protected ResponseEntity<Object> handleAggregateState(AggregateStateException ex, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "Invalid order state", ex.getMessage(), request, "ORDER_STATE_CONFLICT");
    }

    @ExceptionHandler(OptimisticConcurrencyControlException.class)
    protected ResponseEntity<Object> handleOptimisticConcurrency(
            OptimisticConcurrencyControlException ex, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage(), request, "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    protected ResponseEntity<Object> handleIdempotencyConflict(IdempotencyConflictException ex, WebRequest request) {
        return problem(HttpStatus.CONFLICT, "Idempotency key conflict", ex.getMessage(), request, "IDEMPOTENCY_CONFLICT");
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    protected ResponseEntity<Object> handleInProgress(IdempotencyInProgressException ex, WebRequest request) {
        ResponseEntity<Object> response = problem(
                HttpStatus.CONFLICT, "Request is already in progress", ex.getMessage(), request, "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        return response;
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    protected ResponseEntity<Object> handlePayloadMismatch(IdempotencyPayloadMismatchException ex, WebRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key mismatch", ex.getMessage(), request, "IDEMPOTENCY_KEY_MISMATCH");
    }

    @ExceptionHandler(NoSuchElementException.class)
    protected ResponseEntity<Object> handleNotFound(NoSuchElementException ex, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request, "RESOURCE_NOT_FOUND");
    }

    /**
     * Adds the {@code code} extension to the problem details of the errors that are raised by
     * the framework itself, so that the {@code code} member is always present.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail detail) {
            Map<String, Object> properties = detail.getProperties();
            if (properties == null || !properties.containsKey("code")) {
                detail.setProperty("code", codeFor(status));
            }
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    private String codeFor(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return httpStatus.name();
        }
        return "HTTP_" + status.value();
    }

    private ResponseEntity<Object> problem(HttpStatus status,
                                           String title,
                                           String detail,
                                           WebRequest request,
                                           String code) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problemDetail.setInstance(instanceOf(request));
        problemDetail.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    private URI instanceOf(WebRequest request) {
        String description = request.getDescription(false);
        String path = description.startsWith("uri=") ? description.substring("uri=".length()) : description;
        return URI.create(path);
    }
}
