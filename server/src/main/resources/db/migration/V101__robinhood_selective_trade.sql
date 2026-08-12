-- Selective trades the user chooses to journal (worked / didn't / mixed) with short notes.

CREATE TABLE robinhood_selective_trade (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    activity_date   DATE           NOT NULL,
    symbol          VARCHAR(32),
    outcome         VARCHAR(16)    NOT NULL,
    note            TEXT,
    account_suffix  VARCHAR(8),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rh_selective_trade_outcome CHECK (outcome IN ('WORKED', 'DIDNT', 'MIXED'))
);

CREATE INDEX idx_rh_selective_trade_owner_date
    ON robinhood_selective_trade (owner_user_id, activity_date);
