package eventsourcing.postgresql.domain.command;

public enum IdempotencyStatus {
    STARTED,
    COMPLETED,
    FAILED
}
