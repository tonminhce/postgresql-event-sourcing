package com.example.eventsourcing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import eventsourcing.postgresql.domain.event.Event;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@ToString(callSuper = true)
public final class OrderAcceptedEvent extends Event {

    private final UUID driverId;

    @JsonCreator
    @Builder
    public OrderAcceptedEvent(@JsonProperty("aggregateId") UUID aggregateId,
                              @JsonProperty("version") int version,
                              @JsonProperty("createdDate") OffsetDateTime createdDate,
                              @JsonProperty("driverId") UUID driverId) {
        super(aggregateId, version, createdDate);
        this.driverId = driverId;
    }

    public OrderAcceptedEvent(UUID aggregateId, int version, UUID driverId) {
        this(aggregateId, version, null, driverId);
    }

    @Nonnull
    @Override
    public String getEventType() {
        return EventType.ORDER_ACCEPTED.toString();
    }
}
