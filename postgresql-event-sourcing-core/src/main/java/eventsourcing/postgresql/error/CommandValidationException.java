package eventsourcing.postgresql.error;

import lombok.NonNull;

/**
 * The command is syntactically valid, but its content violates a business rule.
 *
 * <p>It is the semantic counterpart of a malformed request: the server
 * understands the request and the content, but it is unable to carry out the
 * contained instruction.
 */
public class CommandValidationException extends RuntimeException {

    public CommandValidationException(@NonNull String message, Object... args) {
        super(message.formatted(args));
    }
}
