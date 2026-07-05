-- Point-in-time Robinhood crypto portfolio snapshots for Reports → Crypto Tracker.
-- Populated once Robinhood Agentic MCP exposes crypto position read tools.

CREATE TABLE robinhood_rh_crypto_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    snapshot_at     TIMESTAMPTZ    NOT NULL,
    snapshot_date   DATE           NOT NULL,
    capture_kind    VARCHAR(16)    NOT NULL DEFAULT 'SCHEDULED',
    total_value     NUMERIC(19, 2) NOT NULL,
    holdings_json   TEXT           NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rh_crypto_snapshot_owner_date
    ON robinhood_rh_crypto_snapshot (owner_user_id, snapshot_date DESC);

CREATE INDEX idx_rh_crypto_snapshot_owner_at
    ON robinhood_rh_crypto_snapshot (owner_user_id, snapshot_at DESC);
