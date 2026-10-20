package com.example.eventsourcing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import eventsourcing.postgresql.domain.event.Event;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@ToString(callSuper = true)
public final class OrderCancelledEvent extends Event {

    private final String reason;

    @JsonCreator
    @Builder
    public OrderCancelledEvent(@JsonProperty("aggregateId") UUID aggregateId,
                               @JsonProperty("version") int version,
                               @JsonProperty("createdDate") OffsetDateTime createdDate,
                               @JsonProperty("reason") String reason) {
        super(aggregateId, version, createdDate);
        this.reason = reason;
    }

    public OrderCancelledEvent(UUID aggregateId, int version, String reason) {
        this(aggregateId, version, null, reason);
    }

    public OrderCancelledEvent(UUID aggregateId, int version) {
        this(aggregateId, version, null, null);
    }

    @Nonnull
    @Override
    public String getEventType() {
        return EventType.ORDER_CANCELLED.toString();
    }
}
