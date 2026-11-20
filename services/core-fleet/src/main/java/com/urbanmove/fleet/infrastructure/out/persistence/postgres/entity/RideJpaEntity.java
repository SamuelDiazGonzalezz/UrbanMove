package com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity;

import com.urbanmove.fleet.domain.model.Ride;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides")
public class RideJpaEntity {

    @Id
    private UUID id;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "start_latitude", nullable = false)
    private double startLatitude;

    @Column(name = "start_longitude", nullable = false)
    private double startLongitude;

    @Column(name = "end_latitude")
    private double endLatitude;

    @Column(name = "end_longitude")
    private double endLongitude;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Column(name = "cost_amount", nullable = false)
    private double costAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Ride.RideStatus status;

    public RideJpaEntity() {}

    public RideJpaEntity(UUID id, UUID reservationId, UUID vehicleId, String userId, Instant startedAt, Instant finishedAt,
                         double startLatitude, double startLongitude, double endLatitude, double endLongitude,
                         double distanceKm, double costAmount, Ride.RideStatus status) {
        this.id = id;
        this.reservationId = reservationId;
        this.vehicleId = vehicleId;
        this.userId = userId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        this.endLatitude = endLatitude;
        this.endLongitude = endLongitude;
        this.distanceKm = distanceKm;
        this.costAmount = costAmount;
        this.status = status;
    }

    public static RideJpaEntity fromDomain(Ride r) {
        return new RideJpaEntity(
                r.id(), r.reservationId(), r.vehicleId(), r.userId(),
                r.startedAt(), r.finishedAt(),
                r.startLatitude(), r.startLongitude(),
                r.endLatitude(), r.endLongitude(),
                r.distanceKm(), r.costAmount(), r.status()
        );
    }

    public Ride toDomain() {
        return new Ride(
                id, reservationId, vehicleId, userId,
                startedAt, finishedAt,
                startLatitude, startLongitude,
                endLatitude, endLongitude,
                distanceKm, costAmount, status
        );
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getVehicleId() { return vehicleId; }
    public String getUserId() { return userId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public double getStartLatitude() { return startLatitude; }
    public double getStartLongitude() { return startLongitude; }
    public double getEndLatitude() { return endLatitude; }
    public double getEndLongitude() { return endLongitude; }
    public double getDistanceKm() { return distanceKm; }
    public double getCostAmount() { return costAmount; }
    public Ride.RideStatus getStatus() { return status; }
}
