package com.example.eventsourcing.controller;

import eventsourcing.postgresql.error.AggregateStateException;
import eventsourcing.postgresql.error.IdempotencyConflictException;
import eventsourcing.postgresql.error.OptimisticConcurrencyControlException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import eventsourcing.postgresql.error.IdempotencyInProgressException;
import eventsourcing.postgresql.error.IdempotencyPayloadMismatchException;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    public record ErrorResponse(String error, String code) {
        public ErrorResponse(String error) {
            this(error, null);
        }
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation error");
        return ResponseEntity.badRequest().body(new ErrorResponse(errorMessage));
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    protected ResponseEntity<ErrorResponse> handlePayloadMismatch(IdempotencyPayloadMismatchException ex) {
        return ResponseEntity.status(UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.getMessage(), "IDEMPOTENCY_KEY_MISMATCH"));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    protected ResponseEntity<ErrorResponse> handleInProgress(IdempotencyInProgressException ex) {
        return ResponseEntity.status(CONFLICT)
                .header("Retry-After", "1")
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({OptimisticConcurrencyControlException.class, IdempotencyConflictException.class})
    protected ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    protected ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(UNPROCESSABLE_ENTITY).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler({AggregateStateException.class, IllegalArgumentException.class})
    protected ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }
}
