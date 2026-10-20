package com.example.eventsourcing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import eventsourcing.postgresql.domain.event.Event;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@ToString(callSuper = true)
public final class OrderCompletedEvent extends Event {

    @JsonCreator
    @Builder
    public OrderCompletedEvent(@JsonProperty("aggregateId") UUID aggregateId,
                               @JsonProperty("version") int version,
                               @JsonProperty("createdDate") OffsetDateTime createdDate) {
        super(aggregateId, version, createdDate);
    }

    public OrderCompletedEvent(UUID aggregateId, int version) {
        this(aggregateId, version, null);
    }

    @Nonnull
    @Override
    public String getEventType() {
        return EventType.ORDER_COMPLETED.toString();
    }
}
