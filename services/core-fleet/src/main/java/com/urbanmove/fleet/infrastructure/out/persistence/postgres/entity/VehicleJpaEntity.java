package com.urbanmove.fleet.infrastructure.out.persistence.postgres.entity;

import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.model.VehicleType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class VehicleJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleStatus status;

    @Column(name = "battery_percentage", nullable = false)
    private int batteryPercentage;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    public VehicleJpaEntity() {}

    public VehicleJpaEntity(UUID id, String code, VehicleType type, VehicleStatus status, int batteryPercentage, double latitude, double longitude) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.status = status;
        this.batteryPercentage = batteryPercentage;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static VehicleJpaEntity fromDomain(Vehicle v) {
        return new VehicleJpaEntity(v.id(), v.code(), v.type(), v.status(), v.batteryPercentage(), v.latitude(), v.longitude());
    }

    public Vehicle toDomain() {
        return new Vehicle(id, code, type, status, batteryPercentage, latitude, longitude);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public VehicleType getType() { return type; }
    public VehicleStatus getStatus() { return status; }
    public int getBatteryPercentage() { return batteryPercentage; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
