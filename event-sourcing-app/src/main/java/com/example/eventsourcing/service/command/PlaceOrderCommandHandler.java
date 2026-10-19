package com.example.eventsourcing.service.command;

import com.example.eventsourcing.domain.command.PlaceOrderCommand;
import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.service.command.CommandHandler;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand> {

    @Override
    public void handle(Aggregate aggregate, Command command) {
        PlaceOrderCommand placeOrderCommand = (PlaceOrderCommand) command;
        log.info("Processing ride order request: aggregateId={}, riderId={}, price={}",
                placeOrderCommand.getAggregateId(), placeOrderCommand.getRiderId(), placeOrderCommand.getPrice());

        aggregate.process(command);

        log.info("Successfully placed ride order: aggregateId={}", aggregate.getAggregateId());
    }

    @Nonnull
    @Override
    public Class<PlaceOrderCommand> getCommandType() {
        return PlaceOrderCommand.class;
    }
}
