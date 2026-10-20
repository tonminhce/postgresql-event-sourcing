package com.example.eventsourcing.domain;

import com.example.eventsourcing.domain.command.AcceptOrderCommand;
import com.example.eventsourcing.domain.command.AdjustOrderPriceCommand;
import com.example.eventsourcing.domain.command.CancelOrderCommand;
import com.example.eventsourcing.domain.command.CompleteOrderCommand;
import com.example.eventsourcing.domain.command.PlaceOrderCommand;
import com.example.eventsourcing.domain.event.OrderAcceptedEvent;
import com.example.eventsourcing.domain.event.OrderCancelledEvent;
import com.example.eventsourcing.domain.event.OrderCompletedEvent;
import com.example.eventsourcing.domain.event.OrderPlacedEvent;
import com.example.eventsourcing.domain.event.OrderPriceAdjustedEvent;
import com.example.eventsourcing.dto.OrderStatus;
import com.example.eventsourcing.dto.WaypointDto;
import com.fasterxml.jackson.annotation.JsonCreator;
import eventsourcing.postgresql.domain.Aggregate;
import eventsourcing.postgresql.error.AggregateStateException;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Getter
@ToString(callSuper = true)
public class OrderAggregate extends Aggregate {

    private OrderStatus status;
    private UUID riderId;
    private BigDecimal price;
    private List<WaypointDto> route;
    private UUID driverId;
    private OffsetDateTime placedDate;
    private OffsetDateTime acceptedDate;
    private OffsetDateTime completedDate;
    private OffsetDateTime cancelledDate;
    private String cancellationReason;

    @JsonCreator
    public OrderAggregate(@NonNull UUID aggregateId, int version) {
        super(aggregateId, version);
    }

    public void process(PlaceOrderCommand command) {
        if (status != null) {
            throw new AggregateStateException("Can't place an order, it's already in status %s", status);
        }
        if (command.getRiderId() == null) {
            throw new IllegalArgumentException("Rider ID cannot be null");
        }
        validatePositivePrice(command.getPrice(), "Price must be strictly positive");
        if (command.getRoute() == null) {
            throw new IllegalArgumentException("Route cannot be null");
        }
        for (WaypointDto wp : command.getRoute()) {
            if (wp == null) {
                throw new IllegalArgumentException("Waypoint cannot be null");
            }
            wp.validate();
        }
        applyChange(OrderPlacedEvent.builder()
                .aggregateId(aggregateId)
                .version(getNextVersion())
                .riderId(command.getRiderId())
                .price(command.getPrice())
                .route(command.getRoute())
                .build());
    }

    public void process(AdjustOrderPriceCommand command) {
        if (!EnumSet.of(OrderStatus.PLACED, OrderStatus.ADJUSTED).contains(status)) {
            throw new AggregateStateException("Can't adjust the price of an order in status %s", status);
        }
        validatePositivePrice(command.getNewPrice(), "New price must be strictly positive");
        if (this.price != null && this.price.compareTo(command.getNewPrice()) == 0) {
            throw new IllegalArgumentException("New price cannot be identical to current price");
        }
        applyChange(OrderPriceAdjustedEvent.builder()
                .aggregateId(aggregateId)
                .version(getNextVersion())
                .newPrice(command.getNewPrice())
                .build());
    }

    public void process(AcceptOrderCommand command) {
        if (!EnumSet.of(OrderStatus.PLACED, OrderStatus.ADJUSTED).contains(status)) {
            throw new AggregateStateException("Can't accept order in status %s", status);
        }
        if (command.getDriverId() == null) {
            throw new IllegalArgumentException("Driver ID cannot be null");
        }
        if (command.getDriverId().equals(this.riderId)) {
            throw new IllegalArgumentException("Driver cannot accept their own ride request");
        }
        applyChange(OrderAcceptedEvent.builder()
                .aggregateId(aggregateId)
                .version(getNextVersion())
                .driverId(command.getDriverId())
                .build());
    }

    public void process(CompleteOrderCommand command) {
        if (status != OrderStatus.ACCEPTED) {
            throw new AggregateStateException("Order in status %s can't be completed", status);
        }
        if (this.driverId == null) {
            throw new AggregateStateException("Cannot complete an order without an assigned driver");
        }
        applyChange(OrderCompletedEvent.builder()
                .aggregateId(aggregateId)
                .version(getNextVersion())
                .build());
    }

    public void process(CancelOrderCommand command) {
        if (!EnumSet.of(OrderStatus.PLACED, OrderStatus.ADJUSTED, OrderStatus.ACCEPTED).contains(status)) {
            throw new AggregateStateException("Order in status %s can't be cancelled", status);
        }
        applyChange(OrderCancelledEvent.builder()
                .aggregateId(aggregateId)
                .version(getNextVersion())
                .reason(command.getReason())
                .build());
    }

    public void apply(OrderPlacedEvent event) {
        this.status = OrderStatus.PLACED;
        this.riderId = event.getRiderId();
        this.price = event.getPrice();
        this.route = event.getRoute();
        this.placedDate = event.getCreatedDate();
    }

    public void apply(OrderPriceAdjustedEvent event) {
        this.status = OrderStatus.ADJUSTED;
        this.price = event.getNewPrice();
    }

    public void apply(OrderAcceptedEvent event) {
        this.status = OrderStatus.ACCEPTED;
        this.driverId = event.getDriverId();
        this.acceptedDate = event.getCreatedDate();
    }

    public void apply(OrderCompletedEvent event) {
        this.status = OrderStatus.COMPLETED;
        this.completedDate = event.getCreatedDate();
    }

    public void apply(OrderCancelledEvent event) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledDate = event.getCreatedDate();
        this.cancellationReason = event.getReason();
    }

    @NonNull
    @Override
    public String getAggregateType() {
        return AggregateType.ORDER.toString();
    }

    private static void validatePositivePrice(BigDecimal price, String errorMessage) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
