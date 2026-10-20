package com.example.eventsourcing.controller;

import com.example.eventsourcing.domain.command.AcceptOrderCommand;
import com.example.eventsourcing.domain.command.AdjustOrderPriceCommand;
import com.example.eventsourcing.domain.command.CancelOrderCommand;
import com.example.eventsourcing.domain.command.CompleteOrderCommand;
import com.example.eventsourcing.domain.command.PlaceOrderCommand;
import com.example.eventsourcing.dto.OrderStatus;
import com.example.eventsourcing.dto.WaypointDto;
import com.example.eventsourcing.projection.OrderProjection;
import com.example.eventsourcing.repository.OrderProjectionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.service.CommandProcessor;
import eventsourcing.postgresql.service.command.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrdersController {

    private final ObjectMapper objectMapper;
    private final CommandProcessor commandProcessor;
    private final OrderProjectionRepository orderProjectionRepository;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<JsonNode> placeOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) throws IOException {

        if (request == null || !request.hasNonNull("riderId") || !request.hasNonNull("price")) {
            throw new IllegalArgumentException("Fields 'riderId' and 'price' are required");
        }

        var existingOrder = idempotencyService.get(idempotencyKey);
        if (existingOrder.isPresent()) {
            return ResponseEntity.status(existingOrder.get().statusCode())
                    .body(objectMapper.readTree(existingOrder.get().responseBody()));
        }

        UUID deterministicOrderId = toDeterministicOrderId(idempotencyKey);
        List<WaypointDto> route = parseRoute(request);

        var order = commandProcessor.process(new PlaceOrderCommand(
                deterministicOrderId,
                UUID.fromString(request.get("riderId").asText()),
                new BigDecimal(request.get("price").asText()),
                route
        ));

        JsonNode responseBody = objectMapper.createObjectNode()
                .put("orderId", order.getAggregateId().toString());

        recordIdempotency(idempotencyKey, "PlaceOrderCommand", order.getAggregateId(),
                objectMapper.writeValueAsString(responseBody), 200);

        return ResponseEntity.ok(responseBody);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<Object> modifyOrder(
            @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) {

        var existing = idempotencyService.get(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().aggregateId().equals(orderId)) {
                return ResponseEntity.status(CONFLICT)
                        .body(objectMapper.createObjectNode().put("error", "Idempotency-Key already used for a different order"));
            }
            return ResponseEntity.status(existing.get().statusCode()).build();
        }

        Command command = toModifyCommand(orderId, request);
        commandProcessor.process(command);

        recordIdempotency(idempotencyKey, command.getClass().getSimpleName(), orderId, "{}", 200);

        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<OrderProjection>> getOrders() {
        return ResponseEntity.ok(orderProjectionRepository.findAll());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderProjection> getOrder(@PathVariable UUID orderId) {
        return orderProjectionRepository
                .findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Command toModifyCommand(UUID orderId, JsonNode request) {
        if (request == null || !request.hasNonNull("status")) {
            throw new IllegalArgumentException("Field 'status' is required");
        }

        OrderStatus status;
        try {
            status = OrderStatus.valueOf(request.get("status").asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status value: " + request.get("status").asText());
        }

        return switch (status) {
            case ADJUSTED -> {
                if (!request.hasNonNull("price")) {
                    throw new IllegalArgumentException("Field 'price' is required for ADJUSTED status");
                }
                yield new AdjustOrderPriceCommand(orderId, new BigDecimal(request.get("price").asText()));
            }
            case ACCEPTED -> {
                if (!request.hasNonNull("driverId")) {
                    throw new IllegalArgumentException("Field 'driverId' is required for ACCEPTED status");
                }
                yield new AcceptOrderCommand(orderId, UUID.fromString(request.get("driverId").asText()));
            }
            case COMPLETED -> new CompleteOrderCommand(orderId);
            case CANCELLED -> {
                String reason = request.has("reason") ? request.get("reason").asText() : null;
                yield new CancelOrderCommand(orderId, reason);
            }
            case PLACED -> throw new IllegalArgumentException("Cannot transition existing order to PLACED status");
        };
    }

    private List<WaypointDto> parseRoute(JsonNode request) throws IOException {
        if (!request.hasNonNull("route")) {
            return List.of();
        }
        return objectMapper.readValue(objectMapper.treeAsTokens(request.get("route")), new TypeReference<>() {});
    }

    private static UUID toDeterministicOrderId(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey)
                ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes(UTF_8))
                : null;
    }

    private void recordIdempotency(String key, String commandType, UUID aggregateId, String responseBody, int statusCode) {
        if (StringUtils.hasText(key)) {
            idempotencyService.record(new IdempotencyKeyRecord(
                    key,
                    commandType,
                    aggregateId,
                    responseBody,
                    statusCode,
                    OffsetDateTime.now()
            ));
        }
    }
}
