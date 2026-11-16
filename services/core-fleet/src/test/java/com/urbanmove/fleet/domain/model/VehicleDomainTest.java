package com.urbanmove.fleet.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleDomainTest {

    @Test
    @DisplayName("Un vehículo disponible con suficiente batería permite reserva")
    void shouldAllowReservationWhenAvailableAndBatterySufficient() {
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                "SCOOT-001",
                VehicleType.SCOOTER,
                VehicleStatus.AVAILABLE,
                80,
                39.4699,
                -0.3763
        );

        assertThat(vehicle.isAvailableForReservation()).isTrue();

        Vehicle reserved = vehicle.markAsReserved();
        assertThat(reserved.status()).isEqualTo(VehicleStatus.RESERVED);
        assertThat(reserved.isAvailableForReservation()).isFalse();
    }

    @Test
    @DisplayName("No permite reservar si la batería es inferior al 15%")
    void shouldNotAllowReservationWhenLowBattery() {
        Vehicle vehicle = new Vehicle(
                UUID.randomUUID(),
                "SCOOT-002",
                VehicleType.SCOOTER,
                VehicleStatus.AVAILABLE,
                10,
                39.4699,
                -0.3763
        );

        assertThat(vehicle.isAvailableForReservation()).isFalse();

        assertThatThrownBy(vehicle::markAsReserved)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be reserved");
    }

    @Test
    @DisplayName("No permite reservar un vehículo que ya está reservado o en mantenimiento")
    void shouldNotAllowReservationWhenNotAvailable() {
        Vehicle inMaintenance = new Vehicle(
                UUID.randomUUID(),
                "SCOOT-003",
                VehicleType.SCOOTER,
                VehicleStatus.MAINTENANCE,
                90,
                39.4699,
                -0.3763
        );

        assertThatThrownBy(inMaintenance::markAsReserved)
                .isInstanceOf(IllegalStateException.class);
    }
}
