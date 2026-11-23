package com.urbanmove.reservations;

import com.urbanmove.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ReservationController {

    public record ReserveRequest(String vehicleId) {
    }

    private final ReservationService reservations;

    public ReservationController(ReservationService reservations) {
        this.reservations = reservations;
    }

    @PostMapping("/api/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public ReservationService.Reservation reserve(@RequestBody ReserveRequest request) {
        return reservations.reserve(request.vehicleId(), CurrentUser.id());
    }

    @GetMapping("/api/reservations/me")
    public List<ReservationService.Reservation> mine() {
        return reservations.byUser(CurrentUser.id());
    }

    @GetMapping("/api/reservations")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public List<ReservationService.Reservation> recent() {
        return reservations.recent();
    }

    @PostMapping("/api/reservations/{id}/cancel")
    public ReservationService.Reservation cancel(@PathVariable String id) {
        return reservations.cancel(id, CurrentUser.id(), CurrentUser.isStaff());
    }

    /** Solo red interna: lo invoca la Lambda reservation-expiration. */
    @PostMapping("/internal/reservations/{id}/expire")
    public Map<String, String> expire(@PathVariable String id) {
        return Map.of("reservationId", id, "result", reservations.expire(id));
    }
}
