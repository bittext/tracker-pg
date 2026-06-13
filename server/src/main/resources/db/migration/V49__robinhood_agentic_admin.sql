-- Admin-managed default guardrails for Robinhood Agentic + approval notification audit.

CREATE TABLE robinhood_agentic_admin_defaults (
    id                              BIGINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    require_approval                BOOLEAN      NOT NULL DEFAULT TRUE,
    max_order_notional              NUMERIC(19, 2),
    allowed_symbols                 TEXT,
    auto_trade_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_trade_kill_switch          BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_trade_require_approval     BOOLEAN      NOT NULL DEFAULT TRUE,
    auto_trade_min_positivity_buy   NUMERIC(6, 2) NOT NULL DEFAULT 15.00,
    auto_trade_max_positivity_sell  NUMERIC(6, 2) NOT NULL DEFAULT -15.00,
    auto_trade_min_spike_z          NUMERIC(8, 4) NOT NULL DEFAULT 1.5000,
    auto_trade_min_mentions_24h     INT          NOT NULL DEFAULT 5,
    auto_trade_order_quantity       NUMERIC(19, 6) NOT NULL DEFAULT 1,
    auto_trade_max_trades_per_day   INT          NOT NULL DEFAULT 3,
    auto_trade_max_daily_notional   NUMERIC(19, 2),
    auto_trade_cooldown_minutes     INT          NOT NULL DEFAULT 60,
    auto_trade_market_hours_only    BOOLEAN      NOT NULL DEFAULT TRUE,
    approval_alert_email_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    approval_alert_sms_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO robinhood_agentic_admin_defaults (id) VALUES (1);

CREATE TABLE robinhood_agentic_approval_notifications (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL,
    order_id            BIGINT       NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    destination_masked  TEXT,
    detail              TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_robinhood_agentic_approval_notifications_order
    ON robinhood_agentic_approval_notifications (order_id, created_at DESC);

CREATE INDEX idx_robinhood_agentic_orders_status_created
    ON robinhood_agentic_orders (status, created_at DESC);
