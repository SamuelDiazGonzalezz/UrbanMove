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

CREATE TABLE IF NOT EXISTS rides (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    user_id VARCHAR(100) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    start_latitude DOUBLE PRECISION NOT NULL,
    start_longitude DOUBLE PRECISION NOT NULL,
    end_latitude DOUBLE PRECISION,
    end_longitude DOUBLE PRECISION,
    distance_km DOUBLE PRECISION NOT NULL,
    cost_amount DOUBLE PRECISION NOT NULL,
    status VARCHAR(30) NOT NULL
);

-- Seed de flota inicial para pruebas del MVP (Sítycleta - Las Palmas de Gran Canaria)
INSERT INTO vehicles (id, code, type, status, battery_percentage, latitude, longitude)
VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'SYT-001', 'ELECTRIC_BICYCLE', 'AVAILABLE', 92, 28.10890, -15.41720), -- Parque de San Telmo
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'SYT-002', 'BICYCLE', 'AVAILABLE', 100, 28.14080, -15.43000),          -- Parque de Santa Catalina
    ('c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'SYT-003', 'ELECTRIC_BICYCLE', 'AVAILABLE', 88, 28.10650, -15.41850), -- Plaza de la Feria
    ('d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'SYT-004', 'BICYCLE', 'AVAILABLE', 100, 28.11891, -15.42492),          -- Plaza O'Shanahan / Base Naval
    ('e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'SYT-005', 'ELECTRIC_BICYCLE', 'MAINTENANCE', 18, 28.10470, -15.43610) -- Schamann
ON CONFLICT (id) DO NOTHING;
