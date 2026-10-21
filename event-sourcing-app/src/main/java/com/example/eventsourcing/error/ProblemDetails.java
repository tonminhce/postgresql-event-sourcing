package com.example.eventsourcing.error;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

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
 *   "type": "urn:problem-type:order-state-conflict",
 *   "title": "Invalid order state",
 *   "status": 409,
 *   "detail": "Order in status COMPLETED can't be cancelled",
 *   "instance": "/orders/48f08af0-e7a5-3451-8ed7-87cc9d131ad1",
 *   "code": "ORDER_STATE_CONFLICT"
 * }
 * </pre>
 *
 * <p>The {@code code} member is an extension of the specification. It is a stable,
 * machine-readable identifier that the clients can branch on, whereas the
 * {@code detail} member is a human-readable message that may change.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ResponseEntity<Object> of(ErrorType type, String detail, String instance) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problemDetail.setTitle(type.title());
        problemDetail.setType(type.type());
        problemDetail.setProperty("code", type.code());
        if (instance != null) {
            problemDetail.setInstance(URI.create(instance));
        }
        return ResponseEntity.status(type.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
