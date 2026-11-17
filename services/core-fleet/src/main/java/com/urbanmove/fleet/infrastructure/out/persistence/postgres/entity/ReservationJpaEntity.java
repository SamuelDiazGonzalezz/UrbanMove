package com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity;

import com.urbanmove.fleet.domain.model.Reservation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class ReservationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reservation.ReservationStatus status;

    public ReservationJpaEntity() {}

    public ReservationJpaEntity(UUID id, UUID vehicleId, String userId, Instant reservedAt, Reservation.ReservationStatus status) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.userId = userId;
        this.reservedAt = reservedAt;
        this.status = status;
    }

    public static ReservationJpaEntity fromDomain(Reservation r) {
        return new ReservationJpaEntity(r.id(), r.vehicleId(), r.userId(), r.reservedAt(), r.status());
    }

    public Reservation toDomain() {
        return new Reservation(id, vehicleId, userId, reservedAt, status);
    }

    public UUID getId() { return id; }
    public UUID getVehicleId() { return vehicleId; }
    public String getUserId() { return userId; }
    public Instant getReservedAt() { return reservedAt; }
    public Reservation.ReservationStatus getStatus() { return status; }
}
