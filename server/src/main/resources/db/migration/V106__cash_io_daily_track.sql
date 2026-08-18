-- End-of-day Inputs/Outputs track: I/O, interest, adjusted cash, live accounts.

CREATE TABLE robinhood_cash_io_daily (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix      VARCHAR(8)     NOT NULL,
    as_of_date          DATE           NOT NULL,
    day_inputs          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    day_outputs         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    day_credits         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    day_debits          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ytd_inputs          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ytd_outputs         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ytd_credits         NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ytd_debits          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    adjusted_now        NUMERIC(19, 2) NOT NULL DEFAULT 0,
    live_value          NUMERIC(19, 2),
    live_accounts_json  TEXT           NOT NULL DEFAULT '[]',
    captured_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_cash_io_daily UNIQUE (owner_user_id, account_suffix, as_of_date)
);

CREATE INDEX idx_rh_cash_io_daily_owner_suffix_date
    ON robinhood_cash_io_daily (owner_user_id, account_suffix, as_of_date DESC);
