package com.example.eventsourcing.domain.command;

import com.example.eventsourcing.domain.AggregateType;
import eventsourcing.postgresql.domain.command.Command;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString(callSuper = true)
public final class CancelOrderCommand extends Command {

    private final String reason;

    public CancelOrderCommand(UUID aggregateId) {
        this(aggregateId, null);
    }

    public CancelOrderCommand(UUID aggregateId, String reason) {
        super(AggregateType.ORDER.toString(), aggregateId);
        this.reason = reason;
    }
}
