package com.example.eventsourcing.domain;

import com.example.eventsourcing.domain.command.AcceptOrderCommand;
import com.example.eventsourcing.domain.command.AdjustOrderPriceCommand;
import com.example.eventsourcing.domain.command.CancelOrderCommand;
import com.example.eventsourcing.domain.command.CompleteOrderCommand;
import com.example.eventsourcing.domain.command.PlaceOrderCommand;
import com.example.eventsourcing.dto.OrderStatus;
import com.example.eventsourcing.dto.WaypointDto;
import com.example.eventsourcing.domain.event.OrderAcceptedEvent;
import com.example.eventsourcing.domain.event.OrderCancelledEvent;
import com.example.eventsourcing.domain.event.OrderCompletedEvent;
import com.example.eventsourcing.domain.event.OrderPlacedEvent;
import com.example.eventsourcing.domain.event.OrderPriceAdjustedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eventsourcing.postgresql.error.AggregateStateException;
import eventsourcing.postgresql.error.CommandValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAggregateDomainTest {

    private final UUID orderId = UUID.randomUUID();
    private final UUID riderId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

    private List<WaypointDto> sampleRoute() {
        return List.of(
                new WaypointDto("Origin", 50.4501, 30.5234),
                new WaypointDto("Destination", 50.4546, 30.5238)
        );
    }

    @Nested
    @DisplayName("Place Order Tests")
    class PlaceOrderTests {

        @Test
        @DisplayName("Should successfully place order with valid parameters")
        void shouldPlaceOrderSuccessfully() {
            var aggregate = new OrderAggregate(orderId, 0);
            var cmd = new PlaceOrderCommand(orderId, riderId, new BigDecimal("120.00"), sampleRoute());

            aggregate.process(cmd);

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PLACED);
            assertThat(aggregate.getRiderId()).isEqualTo(riderId);
            assertThat(aggregate.getPrice()).isEqualTo(new BigDecimal("120.00"));
            assertThat(aggregate.getRoute()).hasSize(2);
            assertThat(aggregate.getChanges()).hasSize(1);
            assertThat(aggregate.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should accept valid boundary coordinates (-90, 90, -180, 180)")
        void shouldAcceptBoundaryCoordinates() {
            var aggregate = new OrderAggregate(orderId, 0);
            var boundaryRoute = List.of(
                    new WaypointDto("North-West Pole", 90.0, -180.0),
                    new WaypointDto("South-East Pole", -90.0, 180.0)
            );
            aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("50.00"), boundaryRoute));

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PLACED);
        }

        @Test
        @DisplayName("Should reject order with null riderId")
        void shouldRejectNullRider() {
            var aggregate = new OrderAggregate(orderId, 0);
            var cmd = new PlaceOrderCommand(orderId, null, new BigDecimal("120.00"), sampleRoute());

            assertThatThrownBy(() -> aggregate.process(cmd))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Rider ID cannot be null");
        }

        @Test
        @DisplayName("Should reject order with non-positive price")
        void shouldRejectZeroOrNegativePrice() {
            var aggregate = new OrderAggregate(orderId, 0);
            var cmdZero = new PlaceOrderCommand(orderId, riderId, BigDecimal.ZERO, sampleRoute());
            var cmdNeg = new PlaceOrderCommand(orderId, riderId, new BigDecimal("-10.00"), sampleRoute());

            assertThatThrownBy(() -> aggregate.process(cmdZero))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Price must be strictly positive");

            assertThatThrownBy(() -> aggregate.process(cmdNeg))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Price must be strictly positive");
        }

        @Test
        @DisplayName("Should reject order with invalid coordinates")
        void shouldRejectInvalidCoordinates() {
            var aggregate = new OrderAggregate(orderId, 0);
            var invalidLatRoute = List.of(new WaypointDto("Bad Lat", 95.0, 30.0));
            var invalidLonRoute = List.of(new WaypointDto("Bad Lon", 50.0, 190.0));

            assertThatThrownBy(() -> aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("50.00"), invalidLatRoute)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Invalid latitude");

            assertThatThrownBy(() -> aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("50.00"), invalidLonRoute)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Invalid longitude");
        }

        @Test
        @DisplayName("Should reject order with null route, null waypoint, or NaN coordinates")
        void shouldRejectInvalidRouteElements() {
            var aggregate = new OrderAggregate(orderId, 0);

            assertThatThrownBy(() -> aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), null)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Route cannot be null");

            assertThatThrownBy(() -> aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), Collections.singletonList(null))))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Waypoint cannot be null");

            var nanRoute = List.of(new WaypointDto("NaN Lat", Double.NaN, 30.0));
            assertThatThrownBy(() -> aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), nanRoute)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Invalid latitude");
        }

        @Test
        @DisplayName("Should reject placing an already placed order")
        void shouldRejectDuplicatePlacement() {
            var aggregate = new OrderAggregate(orderId, 0);
            aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute()));

            assertThatThrownBy(() -> aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute())))
                    .isInstanceOf(AggregateStateException.class)
                    .hasMessageContaining("already in status PLACED");
        }
    }

    @Nested
    @DisplayName("Adjust Order Price Tests")
    class AdjustPriceTests {

        private OrderAggregate createPlacedOrder() {
            var aggregate = new OrderAggregate(orderId, 0);
            aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute()));
            return aggregate;
        }

        @Test
        @DisplayName("Should adjust price successfully")
        void shouldAdjustPrice() {
            var aggregate = createPlacedOrder();
            aggregate.process(new AdjustOrderPriceCommand(orderId, new BigDecimal("150.00")));

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.ADJUSTED);
            assertThat(aggregate.getPrice()).isEqualTo(new BigDecimal("150.00"));
            assertThat(aggregate.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should reject adjustment to identical price")
        void shouldRejectSamePrice() {
            var aggregate = createPlacedOrder();

            assertThatThrownBy(() -> aggregate.process(new AdjustOrderPriceCommand(orderId, new BigDecimal("100.00"))))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("identical to current price");
        }

        @Test
        @DisplayName("Should reject non-positive adjusted price")
        void shouldRejectNonPositiveAdjustedPrice() {
            var aggregate = createPlacedOrder();

            assertThatThrownBy(() -> aggregate.process(new AdjustOrderPriceCommand(orderId, BigDecimal.ZERO)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("New price must be strictly positive");

            assertThatThrownBy(() -> aggregate.process(new AdjustOrderPriceCommand(orderId, new BigDecimal("-20.00"))))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("New price must be strictly positive");
        }

        @Test
        @DisplayName("Should reject adjusting price in ACCEPTED status")
        void shouldRejectAdjustingPriceInAcceptedStatus() {
            var aggregate = createPlacedOrder();
            aggregate.process(new AcceptOrderCommand(orderId, driverId));

            assertThatThrownBy(() -> aggregate.process(new AdjustOrderPriceCommand(orderId, new BigDecimal("200.00"))))
                    .isInstanceOf(AggregateStateException.class)
                    .hasMessageContaining("Can't adjust the price of an order in status ACCEPTED");
        }
    }

    @Nested
    @DisplayName("Accept Order Tests")
    class AcceptOrderTests {

        private OrderAggregate createPlacedOrder() {
            var aggregate = new OrderAggregate(orderId, 0);
            aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute()));
            return aggregate;
        }

        @Test
        @DisplayName("Should reject accepting an unplaced order")
        void shouldRejectAcceptingUnplacedOrder() {
            var aggregate = new OrderAggregate(orderId, 0);

            assertThatThrownBy(() -> aggregate.process(new AcceptOrderCommand(orderId, driverId)))
                    .isInstanceOf(AggregateStateException.class)
                    .hasMessageContaining("Can't accept order in status null");
        }

        @Test
        @DisplayName("Should accept order successfully with distinct driver")
        void shouldAcceptOrder() {
            var aggregate = createPlacedOrder();
            aggregate.process(new AcceptOrderCommand(orderId, driverId));

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(aggregate.getDriverId()).isEqualTo(driverId);
            assertThat(aggregate.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should accept order in ADJUSTED status")
        void shouldAcceptOrderInAdjustedStatus() {
            var aggregate = createPlacedOrder();
            aggregate.process(new AdjustOrderPriceCommand(orderId, new BigDecimal("130.00")));
            aggregate.process(new AcceptOrderCommand(orderId, driverId));

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(aggregate.getDriverId()).isEqualTo(driverId);
            assertThat(aggregate.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should reject driver accepting their own ride")
        void shouldRejectSelfAcceptance() {
            var aggregate = createPlacedOrder();

            assertThatThrownBy(() -> aggregate.process(new AcceptOrderCommand(orderId, riderId)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Driver cannot accept their own ride request");
        }

        @Test
        @DisplayName("Should reject null driver")
        void shouldRejectNullDriver() {
            var aggregate = createPlacedOrder();

            assertThatThrownBy(() -> aggregate.process(new AcceptOrderCommand(orderId, null)))
                    .isInstanceOf(CommandValidationException.class)
                    .hasMessageContaining("Driver ID cannot be null");
        }
    }

    @Nested
    @DisplayName("Complete & Cancel Order Tests")
    class CompleteAndCancelTests {

        private OrderAggregate createAcceptedOrder() {
            var aggregate = new OrderAggregate(orderId, 0);
            aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute()));
            aggregate.process(new AcceptOrderCommand(orderId, driverId));
            return aggregate;
        }

        @Test
        @DisplayName("Should complete accepted order successfully")
        void shouldCompleteAcceptedOrder() {
            var aggregate = createAcceptedOrder();
            aggregate.process(new CompleteOrderCommand(orderId));

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(aggregate.getCompletedDate()).isNotNull();
            assertThat(aggregate.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should reject completing an order not in ACCEPTED status")
        void shouldRejectCompleteWhenNotAccepted() {
            var aggregate = new OrderAggregate(orderId, 0);
            aggregate.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute()));

            assertThatThrownBy(() -> aggregate.process(new CompleteOrderCommand(orderId)))
                    .isInstanceOf(AggregateStateException.class)
                    .hasMessageContaining("can't be completed");
        }

        @Test
        @DisplayName("Should cancel order with custom reason")
        void shouldCancelWithReason() {
            var aggregate = createAcceptedOrder();
            aggregate.process(new CancelOrderCommand(orderId, "Rider requested cancellation"));

            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(aggregate.getCancellationReason()).isEqualTo("Rider requested cancellation");
            assertThat(aggregate.getCancelledDate()).isNotNull();
        }

        @Test
        @DisplayName("Should allow cancelling order in PLACED and ADJUSTED status")
        void shouldCancelInPlacedAndAdjustedStatus() {
            var placedOrder = new OrderAggregate(orderId, 0);
            placedOrder.process(new PlaceOrderCommand(orderId, riderId, new BigDecimal("100.00"), sampleRoute()));
            placedOrder.process(new CancelOrderCommand(orderId, "Cancelled from PLACED"));
            assertThat(placedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            var adjustedOrder = new OrderAggregate(UUID.randomUUID(), 0);
            adjustedOrder.process(new PlaceOrderCommand(adjustedOrder.getAggregateId(), riderId, new BigDecimal("100.00"), sampleRoute()));
            adjustedOrder.process(new AdjustOrderPriceCommand(adjustedOrder.getAggregateId(), new BigDecimal("120.00")));
            adjustedOrder.process(new CancelOrderCommand(adjustedOrder.getAggregateId(), "Cancelled from ADJUSTED"));
            assertThat(adjustedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should reject cancelling a completed order")
        void shouldRejectCancelWhenCompleted() {
            var aggregate = createAcceptedOrder();
            aggregate.process(new CompleteOrderCommand(orderId));

            assertThatThrownBy(() -> aggregate.process(new CancelOrderCommand(orderId, "Too late")))
                    .isInstanceOf(AggregateStateException.class)
                    .hasMessageContaining("can't be cancelled");
        }
    }

    @Nested
    @DisplayName("Historical Event Hydration & Timestamp Tests")
    class HistoricalEventHydrationTests {

        private final ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        @Test
        @DisplayName("Should preserve historical timestamps when loading aggregate from events")
        void shouldPreserveHistoricalTimestampsOnHydration() {
            OffsetDateTime pastPlacedDate = OffsetDateTime.of(2025, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
            OffsetDateTime pastAcceptedDate = pastPlacedDate.plusMinutes(5);

            var event1 = OrderPlacedEvent.builder()
                    .aggregateId(orderId)
                    .version(1)
                    .createdDate(pastPlacedDate)
                    .riderId(riderId)
                    .price(new BigDecimal("100.00"))
                    .route(sampleRoute())
                    .build();

            var event2 = OrderAcceptedEvent.builder()
                    .aggregateId(orderId)
                    .version(2)
                    .createdDate(pastAcceptedDate)
                    .driverId(driverId)
                    .build();

            var aggregate = new OrderAggregate(orderId, 0);
            aggregate.loadFromHistory(List.of(event1, event2));

            assertThat(aggregate.getVersion()).isEqualTo(2);
            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(aggregate.getPlacedDate()).isEqualTo(pastPlacedDate);
            assertThat(aggregate.getAcceptedDate()).isEqualTo(pastAcceptedDate);
        }

        @Test
        @DisplayName("Should preserve createdDate across Jackson JSON serialization and deserialization")
        void shouldPreserveCreatedDateAcrossJsonSerialization() throws Exception {
            OffsetDateTime historicalDate = OffsetDateTime.of(2024, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);

            var originalEvent = OrderPlacedEvent.builder()
                    .aggregateId(orderId)
                    .version(1)
                    .createdDate(historicalDate)
                    .riderId(riderId)
                    .price(new BigDecimal("250.00"))
                    .route(sampleRoute())
                    .build();

            String json = objectMapper.writeValueAsString(originalEvent);
            OrderPlacedEvent deserialized = objectMapper.readValue(json, OrderPlacedEvent.class);

            assertThat(deserialized.getCreatedDate()).isEqualTo(historicalDate);
            assertThat(deserialized.getAggregateId()).isEqualTo(orderId);
            assertThat(deserialized.getPrice()).isEqualByComparingTo("250.00");
        }
    }
}
