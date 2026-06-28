-- Daily 9 PM Central account snapshots for Reports → Robinhood Daily Tracker.

CREATE TABLE robinhood_rh_daily_snapshot (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    snapshot_at          TIMESTAMPTZ    NOT NULL,
    snapshot_date        DATE           NOT NULL,
    account_suffix       VARCHAR(8)     NOT NULL,
    account_number       VARCHAR(32),
    label                VARCHAR(128)   NOT NULL,
    account_kind         VARCHAR(16)    NOT NULL,
    total_account_value  NUMERIC(19, 2) NOT NULL,
    cash_balance         NUMERIC(19, 2) NOT NULL,
    equity_market_value  NUMERIC(19, 2) NOT NULL,
    period_added         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    period_removed       NUMERIC(19, 2) NOT NULL DEFAULT 0,
    period_value_change  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    period_start_date    DATE,
    holdings_json        TEXT           NOT NULL DEFAULT '[]',
    flows_json           TEXT           NOT NULL DEFAULT '[]',
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_daily_snapshot_owner_date_suffix UNIQUE (owner_user_id, snapshot_date, account_suffix)
);

CREATE INDEX idx_rh_daily_snapshot_owner_date
    ON robinhood_rh_daily_snapshot (owner_user_id, snapshot_date DESC);

CREATE INDEX idx_rh_daily_snapshot_owner_suffix_date
    ON robinhood_rh_daily_snapshot (owner_user_id, account_suffix, snapshot_date DESC);
