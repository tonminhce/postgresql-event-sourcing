package com.example.eventsourcing.service.command;

import com.example.eventsourcing.domain.command.CancelOrderCommand;
import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.service.command.CommandHandler;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand> {

    @Override
    public void handle(Aggregate aggregate, Command command) {
        CancelOrderCommand cancelCommand = (CancelOrderCommand) command;
        log.info("Processing order cancellation: aggregateId={}, reason={}",
                cancelCommand.getAggregateId(), cancelCommand.getReason());

        aggregate.process(command);

        log.info("Successfully cancelled order: aggregateId={}, reason={}",
                aggregate.getAggregateId(), cancelCommand.getReason());
    }

    @Nonnull
    @Override
    public Class<CancelOrderCommand> getCommandType() {
        return CancelOrderCommand.class;
    }
}
