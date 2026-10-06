-- Robinhood Predict (event contracts). Separate from finance_predicts_* community sentiment.

CREATE TABLE rh_predict_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL REFERENCES auth_users (id),
    last_synced_at  TIMESTAMPTZ NOT NULL,
    open_value      NUMERIC(19, 6),
    realized_all    NUMERIC(19, 6) NOT NULL DEFAULT 0,
    realized_week   NUMERIC(19, 6) NOT NULL DEFAULT 0,
    close_count     INTEGER NOT NULL DEFAULT 0,
    warnings        TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rh_predict_snapshot_owner UNIQUE (owner_user_id)
);

CREATE TABLE rh_predict_close (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL REFERENCES auth_users (id),
    account_suffix  VARCHAR(8) NOT NULL,
    account_label   VARCHAR(64) NOT NULL,
    fingerprint     VARCHAR(160) NOT NULL,
    closed_at       TIMESTAMPTZ NOT NULL,
    quantity        NUMERIC(19, 6) NOT NULL,
    price           NUMERIC(19, 6) NOT NULL,
    realized        NUMERIC(19, 6) NOT NULL,
    label           TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_rh_predict_close_owner_fingerprint UNIQUE (owner_user_id, fingerprint)
);

CREATE INDEX idx_rh_predict_close_owner_closed
    ON rh_predict_close (owner_user_id, closed_at DESC);
