-- Robinhood Agentic Phase 3: AI auto-trade on Agentic account (max controls).

ALTER TABLE robinhood_agentic_settings
    ADD COLUMN auto_trade_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN auto_trade_kill_switch       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN auto_trade_require_approval  BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN auto_trade_min_positivity_buy  NUMERIC(6, 2) NOT NULL DEFAULT 15.00,
    ADD COLUMN auto_trade_max_positivity_sell NUMERIC(6, 2) NOT NULL DEFAULT -15.00,
    ADD COLUMN auto_trade_min_spike_z       NUMERIC(8, 4) NOT NULL DEFAULT 1.5000,
    ADD COLUMN auto_trade_min_mentions_24h  INT          NOT NULL DEFAULT 5,
    ADD COLUMN auto_trade_order_quantity    NUMERIC(19, 6) NOT NULL DEFAULT 1,
    ADD COLUMN auto_trade_max_trades_per_day INT         NOT NULL DEFAULT 3,
    ADD COLUMN auto_trade_max_daily_notional NUMERIC(19, 2),
    ADD COLUMN auto_trade_cooldown_minutes  INT          NOT NULL DEFAULT 60,
    ADD COLUMN auto_trade_market_hours_only BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN auto_trade_last_run_at       TIMESTAMPTZ,
    ADD COLUMN auto_trade_last_run_message  TEXT;

ALTER TABLE robinhood_agentic_orders
    ADD COLUMN source             VARCHAR(16) NOT NULL DEFAULT 'manual',
    ADD COLUMN auto_signal_json   TEXT;

CREATE INDEX idx_robinhood_agentic_orders_owner_source_created
    ON robinhood_agentic_orders (owner_user_id, source, created_at DESC);

CREATE TABLE robinhood_agentic_auto_trade_runs (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT       NOT NULL,
    started_at           TIMESTAMPTZ  NOT NULL,
    finished_at          TIMESTAMPTZ,
    status               VARCHAR(32)  NOT NULL,
    tickers_evaluated    INT          NOT NULL DEFAULT 0,
    signals_generated    INT          NOT NULL DEFAULT 0,
    orders_reviewed      INT          NOT NULL DEFAULT 0,
    orders_placed        INT          NOT NULL DEFAULT 0,
    message              TEXT
);

CREATE INDEX idx_robinhood_agentic_auto_trade_runs_owner_started
    ON robinhood_agentic_auto_trade_runs (owner_user_id, started_at DESC);
