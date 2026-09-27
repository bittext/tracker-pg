-- Individual ••••3370 margin peeks (hourly Daily Tracker + on-demand) and call-band alerts.

CREATE TABLE rh_individual_margin_peek (
    id                         BIGSERIAL PRIMARY KEY,
    owner_user_id              BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix             VARCHAR(8)     NOT NULL DEFAULT '3370',
    captured_at                TIMESTAMPTZ    NOT NULL,
    snapshot_date              DATE           NOT NULL,
    capture_kind               VARCHAR(16)    NOT NULL,
    cash_balance               NUMERIC(19, 2) NOT NULL,
    equity_market_value        NUMERIC(19, 2) NOT NULL,
    portfolio_value            NUMERIC(19, 2) NOT NULL,
    options_value              NUMERIC(19, 2),
    buying_power               NUMERIC(19, 2),
    unleveraged_buying_power   NUMERIC(19, 2),
    margin_debit               NUMERIC(19, 2) NOT NULL,
    borrow_percent             NUMERIC(8, 2)  NOT NULL,
    annual_rate_percent        NUMERIC(8, 4)  NOT NULL DEFAULT 4.75,
    daily_interest             NUMERIC(19, 2) NOT NULL,
    maintenance_requirement    NUMERIC(19, 2),
    maintenance_source         VARCHAR(16),
    buffer_amount              NUMERIC(19, 2),
    buffer_percent             NUMERIC(8, 2),
    near_call                  BOOLEAN        NOT NULL DEFAULT FALSE,
    high_borrow                BOOLEAN        NOT NULL DEFAULT FALSE,
    risk_status                VARCHAR(16)    NOT NULL DEFAULT 'UNKNOWN',
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_individual_margin_peek_owner_suffix_at
        UNIQUE (owner_user_id, account_suffix, captured_at)
);

CREATE INDEX idx_rh_individual_margin_peek_owner_date
    ON rh_individual_margin_peek (owner_user_id, account_suffix, snapshot_date DESC, captured_at DESC);

CREATE TABLE rh_individual_margin_alert_event (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix      VARCHAR(8)     NOT NULL DEFAULT '3370',
    peek_id             BIGINT         REFERENCES rh_individual_margin_peek (id) ON DELETE SET NULL,
    event_kind          VARCHAR(32)    NOT NULL,
    buffer_percent      NUMERIC(8, 2),
    borrow_percent      NUMERIC(8, 2),
    email_status        VARCHAR(16)    NOT NULL,
    destination_masked  TEXT,
    detail              TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rh_individual_margin_alert_owner_created
    ON rh_individual_margin_alert_event (owner_user_id, created_at DESC);
