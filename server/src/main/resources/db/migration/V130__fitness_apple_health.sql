-- Apple Health export (workouts and body weight), kept beside the manual exercise and weight logs.

CREATE TABLE fitness_apple_health_import (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL REFERENCES auth_users (id),
    file_name       VARCHAR(255) NOT NULL,
    imported_at     TIMESTAMPTZ NOT NULL,
    workouts_added  INTEGER NOT NULL DEFAULT 0,
    weights_added   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_fitness_apple_health_import_owner
    ON fitness_apple_health_import (owner_user_id, imported_at DESC);

CREATE TABLE fitness_apple_health_workout (
    id                BIGSERIAL PRIMARY KEY,
    owner_user_id     BIGINT NOT NULL REFERENCES auth_users (id),
    fingerprint       VARCHAR(64) NOT NULL,
    activity          VARCHAR(80) NOT NULL,
    source_name       VARCHAR(128),
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    day               DATE NOT NULL,
    duration_minutes  INTEGER NOT NULL DEFAULT 0,
    distance_miles    NUMERIC(8, 3),
    energy_kcal       NUMERIC(8, 1),
    CONSTRAINT uq_fitness_apple_health_workout UNIQUE (owner_user_id, fingerprint)
);

CREATE INDEX idx_fitness_apple_health_workout_day
    ON fitness_apple_health_workout (owner_user_id, day);

CREATE TABLE fitness_apple_health_weight (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL REFERENCES auth_users (id),
    fingerprint     VARCHAR(64) NOT NULL,
    logged_on       DATE NOT NULL,
    measured_at     TIMESTAMPTZ NOT NULL,
    weight_lb       NUMERIC(8, 3) NOT NULL,
    source_name     VARCHAR(128),
    CONSTRAINT uq_fitness_apple_health_weight UNIQUE (owner_user_id, fingerprint)
);

CREATE INDEX idx_fitness_apple_health_weight_day
    ON fitness_apple_health_weight (owner_user_id, logged_on);
