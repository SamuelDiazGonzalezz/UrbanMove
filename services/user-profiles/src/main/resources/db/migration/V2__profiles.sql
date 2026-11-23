CREATE TABLE user_profile (
    user_id           TEXT PRIMARY KEY,
    username          TEXT        NOT NULL,
    email             TEXT,
    full_name         TEXT,
    phone             TEXT,
    preferred_zone_id TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
