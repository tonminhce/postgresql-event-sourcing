package com.example.eventsourcing.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ModifyOrderRequest(
        @NotNull(message = "Field 'status' is required")
        OrderStatus status,

        BigDecimal price,
        UUID driverId,
        String reason
) {
}
