package com.urbanmove.availability;

/** Ciclo de vida del recurso (enunciado §5, ARCHITECTURE.md §7). */
public enum VehicleState {
    AVAILABLE,
    RESERVED,
    IN_USE,
    LOW_BATTERY,
    WITHDRAWN,
    CHARGING,
    REPAIRING,
    READY_FOR_SERVICE,
    RETIRED
}
