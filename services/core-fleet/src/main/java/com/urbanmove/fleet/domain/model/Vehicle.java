package com.urbanmove.fleet.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Vehicle(
        UUID id,
        String code,
        VehicleType type,
        VehicleStatus status,
        int batteryPercentage,
        double latitude,
        double longitude
) {
    public Vehicle {
        Objects.requireNonNull(id, "Vehicle id cannot be null");
        Objects.requireNonNull(code, "Vehicle code cannot be null");
        Objects.requireNonNull(type, "Vehicle type cannot be null");
        Objects.requireNonNull(status, "Vehicle status cannot be null");
        if (batteryPercentage < 0 || batteryPercentage > 100) {
            throw new IllegalArgumentException("Battery percentage must be between 0 and 100");
        }
    }

    public boolean isAvailableForReservation() {
        return this.status == VehicleStatus.AVAILABLE && this.batteryPercentage >= 15;
    }

    public Vehicle markAsReserved() {
        if (!isAvailableForReservation()) {
            throw new IllegalStateException("Vehicle " + code + " cannot be reserved in current state: " + status + " (" + batteryPercentage + "%)");
        }
        return new Vehicle(id, code, type, VehicleStatus.RESERVED, batteryPercentage, latitude, longitude);
    }
}
