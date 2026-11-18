-- Brief notes pinned to one row of the Predict fresh-start tables (a Central day or a single close).
-- note_key looks like 'predict-fresh-v1:day:2026-11-17' or 'predict-fresh-v1:close:123'.

CREATE TABLE rh_predict_row_note (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    note_key        VARCHAR(80)   NOT NULL,
    body            VARCHAR(140)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_predict_row_note_owner_key UNIQUE (owner_user_id, note_key)
);
