package com.urbanmove.fleet.concurrency;

import com.urbanmove.fleet.application.service.ReservationService;
import com.urbanmove.fleet.domain.exception.ConcurrencyConflictException;
import com.urbanmove.fleet.domain.model.Reservation;
import com.urbanmove.fleet.domain.model.Vehicle;
import com.urbanmove.fleet.domain.model.VehicleStatus;
import com.urbanmove.fleet.domain.model.VehicleType;
import com.urbanmove.fleet.domain.port.out.EventPublisherPort;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.adapter.PostgresReservationRepositoryAdapter;
import com.urbanmove.fleet.infrastructure.out.persistence.postgres.adapter.PostgresVehicleRepositoryAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class FleetConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PostgresVehicleRepositoryAdapter vehicleRepository;

    @Autowired
    private PostgresReservationRepositoryAdapter reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @MockBean
    private EventPublisherPort eventPublisher;

    @Test
    @DisplayName("Concurrencia extrema: 10 hilos compiten por reservar el mismo vehículo -> exactamente 1 tiene éxito (201) y 9 fallan (409)")
    @Timeout(60)
    void shouldAllowExactlyOneReservationUnderHeavyConcurrency() throws InterruptedException {
        // Given: vehículo disponible y con batería
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle(
                vehicleId, "CONCURRENCY-BIKE-" + UUID.randomUUID().toString().substring(0, 5),
                VehicleType.ELECTRIC_BICYCLE, VehicleStatus.AVAILABLE,
                95, 28.11, -15.42
        );
        vehicleRepository.save(vehicle);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        // When: 10 hilos disparan reserve() simultáneamente
        for (int i = 0; i < threads; i++) {
            final String userId = "user-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Sincroniza el disparo simultáneo de todos los hilos
                    reservationService.reserve(vehicleId, userId);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    if (ex instanceof ConcurrencyConflictException
                            || ex instanceof DataIntegrityViolationException) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Señal de salida para los 10 hilos
        boolean finishedInTime = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then:
        assertThat(finishedInTime).as("El test de concurrencia debe terminar en menos de 60s").isTrue();
        assertThat(unexpectedErrors).as("No debe haber errores inesperados: " + unexpectedErrors).isEmpty();
        assertThat(successCount.get()).as("Exactamente 1 petición debe conseguir la reserva").isEqualTo(1);
        assertThat(conflictCount.get()).as("Las restantes 9 peticiones concurrentes deben ser rechazadas con conflicto").isEqualTo(9);

        // Verificación de estado final en base de datos
        Vehicle updatedVehicle = vehicleRepository.findById(vehicleId).orElseThrow();
        assertThat(updatedVehicle.status()).isEqualTo(VehicleStatus.RESERVED);

        Reservation activeReservation = reservationRepository.findActiveByVehicleId(vehicleId)
                .orElseThrow(() -> new AssertionError("Debe existir una reserva ACTIVE para el vehículo"));
        assertThat(activeReservation.status()).isEqualTo(Reservation.ReservationStatus.ACTIVE);
        assertThat(activeReservation.vehicleId()).isEqualTo(vehicleId);
    }
}
