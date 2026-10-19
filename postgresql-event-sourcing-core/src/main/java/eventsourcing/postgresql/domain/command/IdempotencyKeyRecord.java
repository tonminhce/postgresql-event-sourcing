package eventsourcing.postgresql.domain.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IdempotencyKeyRecord(
        String key,
        String commandType,
        UUID aggregateId,
        String responseBody,
        int statusCode,
        OffsetDateTime createdAt
) {
}
