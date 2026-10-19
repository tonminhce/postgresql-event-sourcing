package eventsourcing.postgresql.domain.event;

import java.math.BigInteger;
import java.time.OffsetDateTime;

public record EventSubscriptionDeadLetterRecord(
        long id,
        String subscriptionName,
        long eventId,
        BigInteger transactionId,
        String aggregateType,
        String eventType,
        String errorMessage,
        String stackTrace,
        int retryCount,
        OffsetDateTime createdAt
) {
}
