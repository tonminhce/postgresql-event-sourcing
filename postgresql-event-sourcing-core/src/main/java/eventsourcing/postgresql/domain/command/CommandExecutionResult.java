package eventsourcing.postgresql.domain.command;

import eventsourcing.postgresql.domain.Aggregate;

public record CommandExecutionResult(
        Aggregate aggregate,
        int statusCode,
        String responseBody,
        boolean replayedFromIdempotency
) {
}
