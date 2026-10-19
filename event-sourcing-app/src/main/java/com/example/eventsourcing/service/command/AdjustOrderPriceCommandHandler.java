package com.example.eventsourcing.service.command;

import com.example.eventsourcing.domain.command.AdjustOrderPriceCommand;
import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.service.command.CommandHandler;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdjustOrderPriceCommandHandler implements CommandHandler<AdjustOrderPriceCommand> {

    @Override
    public void handle(Aggregate aggregate, Command command) {
        AdjustOrderPriceCommand adjustPriceCommand = (AdjustOrderPriceCommand) command;
        log.info("Processing price adjustment: aggregateId={}, newPrice={}",
                adjustPriceCommand.getAggregateId(), adjustPriceCommand.getNewPrice());

        aggregate.process(command);

        log.info("Successfully adjusted order price: aggregateId={}, newVersion={}",
                aggregate.getAggregateId(), aggregate.getVersion());
    }

    @Nonnull
    @Override
    public Class<AdjustOrderPriceCommand> getCommandType() {
        return AdjustOrderPriceCommand.class;
    }
}
