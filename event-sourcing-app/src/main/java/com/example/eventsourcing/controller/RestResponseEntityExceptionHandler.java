package com.example.eventsourcing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventsourcing.postgresql.error.AggregateStateException;
import lombok.RequiredArgsConstructor;
import eventsourcing.postgresql.error.OptimisticConcurrencyControlException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

@ControllerAdvice
@RequiredArgsConstructor
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(value = {OptimisticConcurrencyControlException.class})
    protected ResponseEntity<JsonNode> handleOccException(OptimisticConcurrencyControlException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(objectMapper.createObjectNode()
                        .put("error", ex.getMessage()));
    }

    @ExceptionHandler(value = {NoSuchElementException.class})
    protected ResponseEntity<JsonNode> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(objectMapper.createObjectNode()
                        .put("error", ex.getMessage()));
    }

    @ExceptionHandler(value = {IllegalStateException.class})
    protected ResponseEntity<JsonNode> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(objectMapper.createObjectNode()
                        .put("error", ex.getMessage()));
    }

    @ExceptionHandler(value = {AggregateStateException.class})
    protected ResponseEntity<JsonNode> handleException(AggregateStateException aggregateStateException) {
        return ResponseEntity.badRequest()
                .body(objectMapper.createObjectNode()
                        .put("error", aggregateStateException.getMessage()));
    }
}

