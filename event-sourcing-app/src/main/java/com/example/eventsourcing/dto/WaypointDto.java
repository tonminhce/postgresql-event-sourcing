package com.example.eventsourcing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import eventsourcing.postgresql.error.CommandValidationException;

public record WaypointDto(
        String address,
        @JsonProperty("lat")
        double latitude,
        @JsonProperty("lon")
        double longitude
) {
    public void validate() {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new CommandValidationException("Invalid latitude: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new CommandValidationException("Invalid longitude: " + longitude);
        }
    }
}
