package com.example.eventsourcing.service.command;

import com.example.eventsourcing.domain.command.CompleteOrderCommand;
import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.service.command.CommandHandler;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CompleteOrderCommandHandler implements CommandHandler<CompleteOrderCommand> {

    @Override
    public void handle(Aggregate aggregate, Command command) {
        CompleteOrderCommand completeCommand = (CompleteOrderCommand) command;
        log.info("Processing order completion: aggregateId={}", completeCommand.getAggregateId());

        aggregate.process(command);

        log.info("Successfully completed order: aggregateId={}", aggregate.getAggregateId());
    }

    @Nonnull
    @Override
    public Class<CompleteOrderCommand> getCommandType() {
        return CompleteOrderCommand.class;
    }
}
