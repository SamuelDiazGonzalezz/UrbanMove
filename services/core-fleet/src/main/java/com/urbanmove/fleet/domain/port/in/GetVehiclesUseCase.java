package com.urbanmove.fleet.domain.port.in;

import com.urbanmove.fleet.domain.model.Vehicle;
import java.util.List;

public interface GetVehiclesUseCase {
    List<Vehicle> getAllVehicles();
    List<Vehicle> getAvailableVehicles();
}
