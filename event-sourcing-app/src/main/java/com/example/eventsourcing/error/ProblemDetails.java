package com.example.eventsourcing.error;

import com.example.eventsourcing.config.ApiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Builds the RFC 9457 problem details of the API.
 *
 * <p>Every error of the API is serialized with the same shape and the same media
 * type, so that the clients can parse the errors of the application and of the
 * framework the same way:
 *
 * <pre>
 * {
 *   "type": "https://github.com/tonminhce/postgresql-event-sourcing/blob/main/docs/api/errors.md#order-state-conflict",
 *   "title": "Invalid order state",
 *   "status": 409,
 *   "detail": "Order in status COMPLETED can't be cancelled",
 *   "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
 *   "code": "ORDER_STATE_CONFLICT"
 * }
 * </pre>
 *
 * <p>The {@code type} member identifies the problem type and points to its
 * documentation. The {@code code} member is an extension of the specification. It is
 * a stable, machine-readable identifier that the clients can branch on, whereas the
 * {@code detail} member is a human-readable message that may change.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetails {

    private final ApiProperties apiProperties;

    public ResponseEntity<Object> of(ErrorType type, String detail, String instance) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problemDetail.setTitle(type.title());
        problemDetail.setType(type.uri(apiProperties.getProblemTypeDocsUrl()));
        problemDetail.setProperty("code", type.code());
        if (instance != null) {
            problemDetail.setInstance(URI.create(instance));
        }
        return ResponseEntity.status(type.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
