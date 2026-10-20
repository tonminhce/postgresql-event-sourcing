package eventsourcing.postgresql.service;

import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.domain.event.Event;
import eventsourcing.postgresql.domain.event.EventWithId;
import eventsourcing.postgresql.domain.command.CommandExecutionResult;
import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.domain.command.IdempotencyStatus;
import eventsourcing.postgresql.error.IdempotencyConflictException;
import eventsourcing.postgresql.error.IdempotencyInProgressException;
import eventsourcing.postgresql.error.IdempotencyPayloadMismatchException;
import eventsourcing.postgresql.service.command.CommandHandler;
import eventsourcing.postgresql.service.command.DefaultCommandHandler;
import eventsourcing.postgresql.service.command.IdempotencyService;
import eventsourcing.postgresql.service.event.SyncEventHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Transactional
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandProcessor {

    private final AggregateStore aggregateStore;
    private final List<CommandHandler<? extends Command>> commandHandlers;
    private final DefaultCommandHandler defaultCommandHandler;
    private final List<SyncEventHandler> aggregateChangesHandlers;
    private final IdempotencyService idempotencyService;

    public Aggregate process(@NonNull Command command) {
        return process(command, null, null, null, 200).aggregate();
    }

    public CommandExecutionResult process(@NonNull Command command,
                                          String idempotencyKey,
                                          Function<Aggregate, String> responseBodySupplier,
                                          int successStatusCode) {
        return process(command, idempotencyKey, null, responseBodySupplier, successStatusCode);
    }

    public CommandExecutionResult process(@NonNull Command command,
                                          String idempotencyKey,
                                          String requestHash,
                                          Function<Aggregate, String> responseBodySupplier,
                                          int successStatusCode) {
        if (StringUtils.hasText(idempotencyKey)) {
            boolean leaseAcquired = idempotencyService.tryAcquireLease(
                    idempotencyKey, command.getClass().getSimpleName(), command.getAggregateId(), requestHash);

            if (!leaseAcquired) {
                IdempotencyKeyRecord record = idempotencyService.get(idempotencyKey)
                        .orElseThrow(() -> new IllegalStateException("Idempotency key state inconsistent"));

                if (record.status() == IdempotencyStatus.STARTED) {
                    throw new IdempotencyInProgressException("Request is currently being processed. Please retry shortly.");
                }

                if (StringUtils.hasText(requestHash) && StringUtils.hasText(record.requestHash())
                        && !record.requestHash().equals(requestHash)) {
                    throw new IdempotencyPayloadMismatchException("Idempotency-Key reused with different payload");
                }

                if (record.aggregateId() != null && !record.aggregateId().equals(command.getAggregateId())) {
                    throw new IdempotencyConflictException("Idempotency-Key already used for a different order");
                }

                log.info("Idempotent replay for key {} and command {}", idempotencyKey, command.getClass().getSimpleName());
                Aggregate aggregate = aggregateStore.readAggregate(command.getAggregateType(), record.aggregateId());
                return new CommandExecutionResult(aggregate, record.statusCode(), record.responseBody(), true);
            }
        }

        try {
            log.debug("Processing command {}", command);
            String aggregateType = command.getAggregateType();
            UUID aggregateId = command.getAggregateId();

            Aggregate aggregate = aggregateStore.readAggregate(aggregateType, aggregateId);

            commandHandlers.stream()
                    .filter(commandHandler -> commandHandler.getCommandType() == command.getClass())
                    .findFirst()
                    .ifPresentOrElse(commandHandler -> {
                        log.debug("Handling command {} with {}",
                                command.getClass().getSimpleName(), commandHandler.getClass().getSimpleName());
                        commandHandler.handle(aggregate, command);
                    }, () -> {
                        log.debug("No specialized handler found, handling command {} with {}",
                                command.getClass().getSimpleName(), defaultCommandHandler.getClass().getSimpleName());
                        defaultCommandHandler.handle(aggregate, command);
                    });

            List<EventWithId<Event>> newEvents = aggregateStore.saveAggregate(aggregate);

            aggregateChangesHandlers.stream()
                    .filter(handler -> handler.getAggregateType().equals(aggregateType))
                    .forEach(handler -> handler.handleEvents(newEvents, aggregate));

            String responseBody = responseBodySupplier != null ? responseBodySupplier.apply(aggregate) : "{}";
            if (StringUtils.hasText(idempotencyKey)) {
                idempotencyService.complete(idempotencyKey, responseBody, successStatusCode);
            }

            return new CommandExecutionResult(aggregate, successStatusCode, responseBody, false);
        } catch (Exception ex) {
            if (StringUtils.hasText(idempotencyKey)) {
                idempotencyService.releaseLease(idempotencyKey);
            }
            throw ex;
        }
    }
}
