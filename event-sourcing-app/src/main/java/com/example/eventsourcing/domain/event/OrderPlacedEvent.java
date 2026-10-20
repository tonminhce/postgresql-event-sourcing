package com.example.eventsourcing.domain.event;

import com.example.eventsourcing.dto.WaypointDto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import eventsourcing.postgresql.domain.event.Event;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@ToString(callSuper = true)
public final class OrderPlacedEvent extends Event {

    private final UUID riderId;
    private final BigDecimal price;
    private final List<WaypointDto> route;

    @JsonCreator
    @Builder
    public OrderPlacedEvent(@JsonProperty("aggregateId") UUID aggregateId,
                            @JsonProperty("version") int version,
                            @JsonProperty("createdDate") OffsetDateTime createdDate,
                            @JsonProperty("riderId") UUID riderId,
                            @JsonProperty("price") BigDecimal price,
                            @JsonProperty("route") List<WaypointDto> route) {
        super(aggregateId, version, createdDate);
        this.riderId = riderId;
        this.price = price;
        this.route = route != null ? List.copyOf(route) : List.of();
    }

    public OrderPlacedEvent(UUID aggregateId,
                            int version,
                            UUID riderId,
                            BigDecimal price,
                            List<WaypointDto> route) {
        this(aggregateId, version, null, riderId, price, route);
    }

    @Nonnull
    @Override
    public String getEventType() {
        return EventType.ORDER_PLACED.toString();
    }
}
