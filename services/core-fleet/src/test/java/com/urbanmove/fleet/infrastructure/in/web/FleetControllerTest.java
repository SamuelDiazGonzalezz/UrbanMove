package com.urbanmove.fleet.infrastructure.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.model.VehicleType;
import com.urbanmove.fleet.domain.port.in.GetVehiclesUseCase;
import com.urbanmove.fleet.domain.port.in.ManageRidesUseCase;
import com.urbanmove.fleet.domain.port.in.ReserveVehicleUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FleetController.class)
class FleetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetVehiclesUseCase getVehiclesUseCase;

    @MockBean
    private ReserveVehicleUseCase reserveVehicleUseCase;

    @MockBean
    private ManageRidesUseCase manageRidesUseCase;

    @Test
    @DisplayName("GET /api/v1/vehicles devuelve lista de vehículos")
    void shouldReturnVehicles() throws Exception {
        UUID id = UUID.randomUUID();
        when(getVehiclesUseCase.getAllVehicles()).thenReturn(List.of(
                new Vehicle(id, "SCOOT-001", VehicleType.SCOOTER, VehicleStatus.AVAILABLE, 90, 39.46, -0.37)
        ));

        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SCOOT-001"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/v1/reservations reserva vehículo con éxito")
    void shouldReserveVehicle() throws Exception {
        UUID vehicleId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Reservation reservation = new Reservation(reservationId, vehicleId, "user-test", Instant.now(), Reservation.ReservationStatus.ACTIVE);

        when(reserveVehicleUseCase.reserve(eq(vehicleId), eq("user-test"))).thenReturn(reservation);

        FleetController.ReserveRequest request = new FleetController.ReserveRequest(vehicleId, "user-test");

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
