package com.example.eventsourcing.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import eventsourcing.postgresql.domain.event.Event;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@ToString(callSuper = true)
public final class OrderPriceAdjustedEvent extends Event {

    private final BigDecimal newPrice;

    @JsonCreator
    @Builder
    public OrderPriceAdjustedEvent(@JsonProperty("aggregateId") UUID aggregateId,
                                   @JsonProperty("version") int version,
                                   @JsonProperty("createdDate") OffsetDateTime createdDate,
                                   @JsonProperty("newPrice") BigDecimal newPrice) {
        super(aggregateId, version, createdDate);
        this.newPrice = newPrice;
    }

    public OrderPriceAdjustedEvent(UUID aggregateId,
                                   int version,
                                   BigDecimal newPrice) {
        this(aggregateId, version, null, newPrice);
    }

    @Nonnull
    @Override
    public String getEventType() {
        return EventType.ORDER_PRICE_ADJUSTED.toString();
    }
}
