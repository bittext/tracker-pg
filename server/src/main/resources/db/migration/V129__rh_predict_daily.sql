-- End-of-day Predict performance snapshots (Central calendar day) for day-by-day review.

CREATE TABLE rh_predict_daily (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL REFERENCES auth_users (id),
    snapshot_date   DATE NOT NULL,
    captured_at     TIMESTAMPTZ NOT NULL,
    closes          INTEGER NOT NULL DEFAULT 0,
    invested        NUMERIC(19, 6) NOT NULL DEFAULT 0,
    payback         NUMERIC(19, 6) NOT NULL DEFAULT 0,
    returned        NUMERIC(19, 6) NOT NULL DEFAULT 0,
    open_value      NUMERIC(19, 6),
    open_cost       NUMERIC(19, 6),
    official        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rh_predict_daily_owner_date UNIQUE (owner_user_id, snapshot_date)
);

CREATE INDEX idx_rh_predict_daily_owner_date
    ON rh_predict_daily (owner_user_id, snapshot_date DESC);
