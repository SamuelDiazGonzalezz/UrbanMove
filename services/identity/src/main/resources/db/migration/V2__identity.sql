-- Espejo administrativo de los usuarios de Keycloak. Las credenciales viven solo en Keycloak.
CREATE TABLE user_account (
    user_id    TEXT PRIMARY KEY,
    username   TEXT        NOT NULL UNIQUE,
    email      TEXT,
    full_name  TEXT,
    roles      TEXT        NOT NULL DEFAULT '',
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
