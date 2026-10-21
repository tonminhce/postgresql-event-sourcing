package com.example.eventsourcing.controller;

import com.example.eventsourcing.domain.OrderAggregate;
import com.example.eventsourcing.domain.command.AcceptOrderCommand;
import com.example.eventsourcing.domain.command.AdjustOrderPriceCommand;
import com.example.eventsourcing.domain.command.CancelOrderCommand;
import com.example.eventsourcing.domain.command.CompleteOrderCommand;
import com.example.eventsourcing.domain.command.PlaceOrderCommand;
import com.example.eventsourcing.dto.ModifyOrderRequest;
import com.example.eventsourcing.dto.PlaceOrderRequest;
import com.example.eventsourcing.mapper.OrderMapper;
import com.example.eventsourcing.projection.OrderProjection;
import com.example.eventsourcing.repository.OrderProjectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventsourcing.postgresql.domain.command.Command;
import eventsourcing.postgresql.domain.command.CommandExecutionResult;
import eventsourcing.postgresql.error.AggregateStateException;
import eventsourcing.postgresql.error.CommandValidationException;
import eventsourcing.postgresql.service.CommandProcessor;
import jakarta.validation.Valid;
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

import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrdersController {

    private final ObjectMapper objectMapper;
    private final CommandProcessor commandProcessor;
    private final OrderProjectionRepository orderProjectionRepository;
    private final OrderMapper orderMapper;

    @PostMapping
    public ResponseEntity<JsonNode> placeOrder(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) throws JsonProcessingException {

        UUID deterministicOrderId = toDeterministicOrderId(idempotencyKey);
        String requestHash = computeRequestHash(request);

        CommandExecutionResult result = commandProcessor.process(
                new PlaceOrderCommand(
                        deterministicOrderId,
                        request.riderId(),
                        request.price(),
                        request.route()
                ),
                idempotencyKey,
                requestHash,
                agg -> "{\"orderId\":\"" + agg.getAggregateId() + "\"}",
                201
        );

        UUID orderId = UUID.fromString(objectMapper.readTree(result.responseBody()).get("orderId").asText());

        var responseBuilder = ResponseEntity.status(result.statusCode())
                .location(URI.create("/orders/" + orderId));
        if (result.replayedFromIdempotency()) {
            responseBuilder.header("Idempotent-Replayed", "true");
            responseBuilder.header("Idempotency-Key", idempotencyKey);
        }

        return responseBuilder.body(objectMapper.readTree(result.responseBody()));
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<OrderProjection> modifyOrder(
            @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ModifyOrderRequest request) {

        String requestHash = computeRequestHash(request);
        Command command = toModifyCommand(orderId, request);
        CommandExecutionResult result = commandProcessor.process(
                command, idempotencyKey, requestHash,
                aggregate -> toProjectionJson((OrderAggregate) aggregate), 200);

        var responseBuilder = ResponseEntity.status(result.statusCode());
        if (result.replayedFromIdempotency()) {
            responseBuilder.header("Idempotent-Replayed", "true");
            responseBuilder.header("Idempotency-Key", idempotencyKey);
        }

        return responseBuilder.body(orderProjectionRepository.findById(orderId).orElseThrow());
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
                .orElseThrow(() -> new NoSuchElementException("Order not found for ID: " + orderId));
    }

    private Command toModifyCommand(UUID orderId, ModifyOrderRequest request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("Field 'status' is required");
        }

        return switch (request.status()) {
            case ADJUSTED -> {
                if (request.price() == null) {
                    throw new CommandValidationException("Field 'price' is required for ADJUSTED status");
                }
                yield new AdjustOrderPriceCommand(orderId, request.price());
            }
            case ACCEPTED -> {
                if (request.driverId() == null) {
                    throw new CommandValidationException("Field 'driverId' is required for ACCEPTED status");
                }
                yield new AcceptOrderCommand(orderId, request.driverId());
            }
            case COMPLETED -> new CompleteOrderCommand(orderId);
            case CANCELLED -> new CancelOrderCommand(orderId, request.reason());
            case PLACED -> throw new AggregateStateException("Cannot transition existing order to PLACED status");
        };
    }

    private String toProjectionJson(OrderAggregate aggregate) {
        try {
            return objectMapper.writeValueAsString(orderMapper.toProjection(aggregate));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the order projection", e);
        }
    }

    private static UUID toDeterministicOrderId(String idempotencyKey) {
        return StringUtils.hasText(idempotencyKey)
                ? UUID.nameUUIDFromBytes(idempotencyKey.getBytes(UTF_8))
                : null;
    }

    private String computeRequestHash(Object request) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(request);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
