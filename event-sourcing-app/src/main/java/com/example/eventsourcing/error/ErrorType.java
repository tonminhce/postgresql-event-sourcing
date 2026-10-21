package com.example.eventsourcing.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.net.URI;
import java.util.Locale;

/**
 * The catalog of the errors of the API.
 *
 * <p>Every error the API can answer with is declared here, together with the HTTP
 * status it maps to and the human-readable title of its problem type. The
 * catalog is the single place where a new error is added, and the single place
 * where the status code of an error is defined.
 *
 * <p>The name of the constant is the machine-readable code of the error. It is
 * returned to the clients in the {@code code} member of the problem details,
 * and it is stable: it never changes, so that the clients can safely branch on
 * it. The {@code type} URI is derived from it, and the {@code title} is the
 * short summary of the problem type.
 */
public enum ErrorType {

    // 400 - the request itself is malformed
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),

    // 404 - the resource does not exist
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    // 409 - the request is valid but conflicts with the current state of the resource
    ORDER_STATE_CONFLICT(HttpStatus.CONFLICT, "Invalid order state"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key conflict"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Request is already in progress"),

    // 422 - the request is well-formed but violates a business rule
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "Validation failed"),
    IDEMPOTENCY_KEY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key mismatch");

    private static final String TYPE_PREFIX = "urn:problem-type:";

    private final HttpStatus status;
    private final String title;

    ErrorType(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /**
     * The stable, machine-readable code of the error.
     */
    public String code() {
        return name();
    }

    /**
     * The URI that identifies the problem type of the error.
     */
    public URI type() {
        return URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /**
     * The code of the errors that are raised by the framework itself and are not
     * part of the catalog, such as an unsupported media type.
     */
    public static String codeFor(HttpStatusCode status) {
        if (status instanceof HttpStatus httpStatus) {
            return httpStatus.name();
        }
        return "HTTP_" + status.value();
    }
}
