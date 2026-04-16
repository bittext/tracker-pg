-- Tracker app schema for PostgreSQL (JPA entity alignment + Robinhood finance table).

CREATE TABLE auth_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(256) NOT NULL,
    password_salt   VARCHAR(128) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    phone_e164      VARCHAR(32),
    mfa_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE TABLE auth_trusted_locations (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES auth_users (id),
    location_hash   VARCHAR(128)  NOT NULL,
    display_label   VARCHAR(180),
    first_seen_at   TIMESTAMPTZ   NOT NULL,
    last_seen_at    TIMESTAMPTZ   NOT NULL
);

CREATE TABLE auth_mfa_challenges (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES auth_users (id),
    otp_hash       VARCHAR(256)   NOT NULL,
    otp_salt       VARCHAR(128)   NOT NULL,
    attempts       INTEGER        NOT NULL,
    max_attempts   INTEGER        NOT NULL,
    expires_at     TIMESTAMPTZ    NOT NULL,
    used_at        TIMESTAMPTZ,
    location_hash  VARCHAR(128)   NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE management_task_categories (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(2000),
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE TABLE management_task_types (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    notes       VARCHAR(2000),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE management_tasks (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(500)  NOT NULL,
    notes         VARCHAR(4000),
    due_date      DATE,
    urgency       VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
    completed     BOOLEAN       NOT NULL DEFAULT FALSE,
    category_id   BIGINT        NOT NULL REFERENCES management_task_categories (id),
    task_type_id  BIGINT        NOT NULL REFERENCES management_task_types (id),
    created_at    TIMESTAMPTZ   NOT NULL
);

CREATE TABLE fitness_exercises (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    category    VARCHAR(255),
    notes       VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE fitness_exercise_day_logs (
    id                 BIGSERIAL PRIMARY KEY,
    exercise_id        BIGINT        NOT NULL REFERENCES fitness_exercises (id),
    performed_on       DATE          NOT NULL,
    notes              VARCHAR(4000) NOT NULL,
    duration_minutes   INTEGER
);

CREATE TABLE fitness_body_weight (
    id          BIGSERIAL PRIMARY KEY,
    logged_on   DATE           NOT NULL,
    weight_kg   NUMERIC(6, 3)  NOT NULL,
    weight_lb   NUMERIC(8, 3),
    notes       VARCHAR(255)
);

CREATE TABLE robinhood_transactions (
    activity_date  TIMESTAMP,
    process_date   TIMESTAMP,
    settle_date    TIMESTAMP,
    instrument     TEXT,
    description    TEXT,
    trans_code     VARCHAR(128),
    quantity       NUMERIC(19, 4),
    price          NUMERIC(19, 4),
    amount         NUMERIC(19, 4)
);

CREATE INDEX idx_robinhood_activity_date ON robinhood_transactions (activity_date);
CREATE INDEX idx_robinhood_instrument_lower ON robinhood_transactions (lower(trim(instrument)));
