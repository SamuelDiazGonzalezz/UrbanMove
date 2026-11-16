-- Base de datos inicial para Fase 0 (walking skeleton)
CREATE DATABASE zones_db;

-- Bases de datos para siguientes fases
CREATE DATABASE identity_db;
CREATE DATABASE user_profiles_db;
CREATE DATABASE inventory_db;
CREATE DATABASE availability_db;
CREATE DATABASE reservations_db;
CREATE DATABASE rides_db;
CREATE DATABASE history_db;
CREATE DATABASE analytics_db;

GRANT ALL PRIVILEGES ON DATABASE zones_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE identity_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE user_profiles_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE availability_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE reservations_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE rides_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE history_db TO urbanmove;
GRANT ALL PRIVILEGES ON DATABASE analytics_db TO urbanmove;
