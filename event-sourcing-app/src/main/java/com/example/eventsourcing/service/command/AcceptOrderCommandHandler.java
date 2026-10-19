package com.example.eventsourcing.service.command;

import com.example.eventsourcing.domain.command.AcceptOrderCommand;
import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.service.command.CommandHandler;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AcceptOrderCommandHandler implements CommandHandler<AcceptOrderCommand> {

    @Override
    public void handle(Aggregate aggregate, Command command) {
        AcceptOrderCommand acceptCommand = (AcceptOrderCommand) command;
        log.info("Processing order acceptance: aggregateId={}, driverId={}",
                acceptCommand.getAggregateId(), acceptCommand.getDriverId());

        aggregate.process(command);

        log.info("Successfully accepted order: aggregateId={}, driverId={}",
                aggregate.getAggregateId(), acceptCommand.getDriverId());
    }

    @Nonnull
    @Override
    public Class<AcceptOrderCommand> getCommandType() {
        return AcceptOrderCommand.class;
    }
}
