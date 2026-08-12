-- Manual inputs (deposits / transfers in) and outputs (withdrawals / transfers out)
-- for Robinhood accounts, tracked in Markets > Trade.

CREATE TABLE robinhood_account_cash_io (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix  VARCHAR(8)     NOT NULL,
    activity_date   DATE           NOT NULL,
    direction       VARCHAR(3)     NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rh_cash_io_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_rh_cash_io_amount CHECK (amount > 0)
);

CREATE INDEX idx_rh_cash_io_owner_date
    ON robinhood_account_cash_io (owner_user_id, activity_date);

CREATE INDEX idx_rh_cash_io_owner_suffix_date
    ON robinhood_account_cash_io (owner_user_id, account_suffix, activity_date);
