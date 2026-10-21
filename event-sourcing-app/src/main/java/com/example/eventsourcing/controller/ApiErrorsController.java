package com.example.eventsourcing.controller;

import com.example.eventsourcing.config.ApiProperties;
import com.example.eventsourcing.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * Exposes the catalog of the errors of the API.
 *
 * <p>The catalog is the single source of truth of the errors the API can answer with.
 * A client, or a generator of a client SDK, can read it to discover the stable codes
 * and the documentation of every error.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiErrorsController {

    private final ApiProperties apiProperties;

    @GetMapping("/errors")
    public ResponseEntity<List<ErrorTypeResponse>> getErrorTypes() {
        List<ErrorTypeResponse> errors = Arrays.stream(ErrorType.values())
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(errors);
    }

    private ErrorTypeResponse toResponse(ErrorType type) {
        URI documentation = type.uri(apiProperties.getProblemTypeDocsUrl());
        return new ErrorTypeResponse(
                type.code(),
                type.status().value(),
                type.title(),
                documentation.toString());
    }

    /**
     * One entry of the catalog of the errors.
     *
     * @param code the stable, machine-readable code of the error
     * @param status the HTTP status of the error
     * @param title the short summary of the problem type
     * @param type the URI that identifies the problem type and points to its documentation
     */
    public record ErrorTypeResponse(
            String code,
            int status,
            String title,
            String type
    ) {
    }
}
