package com.example.eventsourcing.controller;

import eventsourcing.postgresql.error.AggregateStateException;
import eventsourcing.postgresql.error.OptimisticConcurrencyControlException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    public record ErrorResponse(String error) {}

    @ExceptionHandler(OptimisticConcurrencyControlException.class)
    protected ResponseEntity<ErrorResponse> handleOcc(OptimisticConcurrencyControlException ex) {
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
