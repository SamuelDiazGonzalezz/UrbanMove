-- Creación de tablas operacionales para Core Fleet
CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    battery_percentage INT NOT NULL CHECK (battery_percentage >= 0 AND battery_percentage <= 100),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS reservations (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    user_id VARCHAR(100) NOT NULL,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL
);

-- Seed de flota inicial para pruebas del MVP (Valencia centro)
INSERT INTO vehicles (id, code, type, status, battery_percentage, latitude, longitude)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SCOOT-001', 'SCOOTER', 'AVAILABLE', 92, 39.4699, -0.3763),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'SCOOT-002', 'SCOOTER', 'AVAILABLE', 75, 39.4715, -0.3750),
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'BIKE-101', 'ELECTRIC_BICYCLE', 'AVAILABLE', 88, 39.4680, -0.3780),
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'BIKE-102', 'BICYCLE', 'AVAILABLE', 100, 39.4730, -0.3795),
    ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'SCOOT-003', 'SCOOTER', 'MAINTENANCE', 10, 39.4670, -0.3740)
ON CONFLICT (id) DO NOTHING;
