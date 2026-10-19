package com.example.eventsourcing.controller;

import com.example.eventsourcing.domain.command.*;
import com.example.eventsourcing.dto.OrderStatus;
import com.example.eventsourcing.dto.WaypointDto;
import com.example.eventsourcing.projection.OrderProjection;
import com.example.eventsourcing.repository.OrderProjectionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventsourcing.postgresql.service.CommandProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import eventsourcing.postgresql.domain.command.IdempotencyKeyRecord;
import eventsourcing.postgresql.service.command.IdempotencyService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
            return ResponseEntity.badRequest()
                    .body(objectMapper.createObjectNode().put("error", "Fields 'riderId' and 'price' are required"));
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyService.get(idempotencyKey);
            if (existing.isPresent()) {
                return ResponseEntity.status(existing.get().statusCode())
                        .body(objectMapper.readTree(existing.get().responseBody()));
            }
        }

        UUID deterministicOrderId = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : null;

        List<WaypointDto> route = (request.hasNonNull("route"))
                ? objectMapper.readValue(objectMapper.treeAsTokens(request.get("route")), new TypeReference<>() {})
                : List.of();

        var order = commandProcessor.process(new PlaceOrderCommand(
                deterministicOrderId,
                UUID.fromString(request.get("riderId").asText()),
                new BigDecimal(request.get("price").asText()),
                route
        ));

        JsonNode responseBody = objectMapper.createObjectNode()
                .put("orderId", order.getAggregateId().toString());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.record(new IdempotencyKeyRecord(
                    idempotencyKey,
                    "PlaceOrderCommand",
                    order.getAggregateId(),
                    objectMapper.writeValueAsString(responseBody),
                    200,
                    OffsetDateTime.now()
            ));
        }

        return ResponseEntity.ok().body(responseBody);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<Object> modifyOrder(
            @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) {
        if (request == null || !request.hasNonNull("status")) {
            return ResponseEntity.badRequest()
                    .body(objectMapper.createObjectNode().put("error", "Field 'status' is required"));
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyService.get(idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().aggregateId().equals(orderId)) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                            .body(objectMapper.createObjectNode().put("error", "Idempotency-Key already used for a different order"));
                }
                return ResponseEntity.status(existing.get().statusCode()).build();
            }
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.get("status").asText());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(objectMapper.createObjectNode().put("error", "Invalid status value: " + request.get("status").asText()));
        }

        switch (newStatus) {
            case ADJUSTED -> {
                if (!request.hasNonNull("price")) {
                    return ResponseEntity.badRequest()
                            .body(objectMapper.createObjectNode().put("error", "Field 'price' is required for ADJUSTED status"));
                }
                commandProcessor.process(new AdjustOrderPriceCommand(
                        orderId,
                        new BigDecimal(request.get("price").asText())
                ));
            }
            case ACCEPTED -> {
                if (!request.hasNonNull("driverId")) {
                    return ResponseEntity.badRequest()
                            .body(objectMapper.createObjectNode().put("error", "Field 'driverId' is required for ACCEPTED status"));
                }
                commandProcessor.process(new AcceptOrderCommand(
                        orderId,
                        UUID.fromString(request.get("driverId").asText())
                ));
            }
            case COMPLETED -> {
                commandProcessor.process(new CompleteOrderCommand(orderId));
            }
            case CANCELLED -> {
                String reason = request.has("reason") ? request.get("reason").asText() : null;
                commandProcessor.process(new CancelOrderCommand(orderId, reason));
            }
            default -> {
                return ResponseEntity.badRequest().build();
            }
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.record(new IdempotencyKeyRecord(
                    idempotencyKey,
                    "ModifyOrder:" + newStatus,
                    orderId,
                    "{}",
                    200,
                    OffsetDateTime.now()
            ));
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/")
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
}
