-- ====================================================================
-- V2: Concurrency, Expiration, and Ride Idempotency Constraints
-- ====================================================================

-- 1. Añadir columna expires_at a reservas con default de 15 minutos para reservas existentes
ALTER TABLE reservations 
ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

UPDATE reservations 
SET expires_at = reserved_at + INTERVAL '15 minutes' 
WHERE expires_at IS NULL;

ALTER TABLE reservations 
ALTER COLUMN expires_at SET NOT NULL;

-- 2. Limpieza de seguridad: Si existiese alguna inconsistencia previa con múltiples reservas ACTIVE para un vehículo,
-- cancelar todas salvo la más reciente antes de crear el índice único parcial.
WITH ranked_reservations AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY vehicle_id ORDER BY reserved_at DESC) as rank
    FROM reservations
    WHERE status = 'ACTIVE'
)
UPDATE reservations
SET status = 'CANCELLED'
WHERE id IN (SELECT id FROM ranked_reservations WHERE rank > 1);

-- 3. Invariante portable: Índice único parcial para evitar doble reserva concurrente
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_reservation_per_vehicle 
ON reservations (vehicle_id) 
WHERE status = 'ACTIVE';

-- 4. Permitir walk-in rides: reservation_id pasa a ser nullable
ALTER TABLE rides 
ALTER COLUMN reservation_id DROP NOT NULL;

-- 5. Idempotencia de viajes: una reserva no puede generar más de un viaje
ALTER TABLE rides 
DROP CONSTRAINT IF EXISTS uq_ride_reservation;

ALTER TABLE rides 
ADD CONSTRAINT uq_ride_reservation UNIQUE (reservation_id);

-- 6. Unicidad de viaje activo: un vehículo no puede tener más de un viaje en curso
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_ride_per_vehicle 
ON rides (vehicle_id) 
WHERE status = 'IN_PROGRESS';
