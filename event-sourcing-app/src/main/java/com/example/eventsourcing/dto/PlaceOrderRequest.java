package com.example.eventsourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull(message = "Fields 'riderId' and 'price' are required")
        UUID riderId,

        @NotNull(message = "Fields 'riderId' and 'price' are required")
        BigDecimal price,

        @Valid
        List<WaypointDto> route
) {
    public List<WaypointDto> route() {
        return route != null ? route : List.of();
    }
}
