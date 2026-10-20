package eventsourcing.postgresql.error;

public class IdempotencyPayloadMismatchException extends RuntimeException {

    public IdempotencyPayloadMismatchException(String message) {
        super(message);
    }
}
