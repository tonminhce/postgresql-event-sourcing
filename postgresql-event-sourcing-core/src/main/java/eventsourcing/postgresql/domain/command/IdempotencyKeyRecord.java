package eventsourcing.postgresql.domain.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdempotencyKeyRecord(
        String key,
        String commandType,
        UUID aggregateId,
        IdempotencyStatus status,
        String requestHash,
        String responseBody,
        Integer statusCode,
        OffsetDateTime createdAt
) {
    public IdempotencyKeyRecord(
            String key,
            String commandType,
            UUID aggregateId,
            String responseBody,
            int statusCode,
            OffsetDateTime createdAt) {
        this(key, commandType, aggregateId, IdempotencyStatus.COMPLETED, null, responseBody, statusCode, createdAt);
    }
}
