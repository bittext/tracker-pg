-- Robinhood Crypto Trading API: Predicts-driven auto-trade settings, orders audit, run log.

CREATE TABLE robinhood_crypto_trading_settings (
    owner_user_id                   BIGINT        PRIMARY KEY,
    auto_trade_enabled              BOOLEAN       NOT NULL DEFAULT FALSE,
    auto_trade_kill_switch          BOOLEAN       NOT NULL DEFAULT FALSE,
    auto_trade_min_positivity_buy   NUMERIC(6, 2) NOT NULL DEFAULT 15.00,
    auto_trade_max_positivity_sell  NUMERIC(6, 2) NOT NULL DEFAULT -15.00,
    auto_trade_min_spike_z          NUMERIC(8, 4) NOT NULL DEFAULT 1.5000,
    auto_trade_min_mentions_24h     INT           NOT NULL DEFAULT 5,
    auto_trade_order_quote_amount   NUMERIC(19, 2) NOT NULL DEFAULT 25.00,
    auto_trade_max_trades_per_day   INT           NOT NULL DEFAULT 3,
    auto_trade_max_daily_notional   NUMERIC(19, 2) DEFAULT 500.00,
    auto_trade_cooldown_minutes     INT           NOT NULL DEFAULT 60,
    allowed_symbols_json            TEXT          NOT NULL DEFAULT '["BTC","ETH"]',
    auto_trade_last_run_at          TIMESTAMPTZ,
    auto_trade_last_run_message     TEXT,
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE robinhood_crypto_orders (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    symbol              VARCHAR(32)  NOT NULL,
    trading_pair        VARCHAR(32)  NOT NULL,
    side                VARCHAR(8)   NOT NULL,
    order_type          VARCHAR(16)  NOT NULL DEFAULT 'market',
    quote_amount        NUMERIC(19, 2),
    asset_quantity      NUMERIC(19, 8),
    estimated_notional  NUMERIC(19, 2),
    client_order_id     VARCHAR(64),
    robinhood_order_id  TEXT,
    source              VARCHAR(16)  NOT NULL DEFAULT 'manual',
    auto_signal_json    TEXT,
    place_json          TEXT,
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    placed_at           TIMESTAMPTZ
);

CREATE INDEX idx_robinhood_crypto_orders_owner_created
    ON robinhood_crypto_orders (owner_user_id, created_at DESC);

CREATE INDEX idx_robinhood_crypto_orders_owner_source_created
    ON robinhood_crypto_orders (owner_user_id, source, created_at DESC);

CREATE TABLE robinhood_crypto_auto_trade_runs (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT       NOT NULL,
    started_at           TIMESTAMPTZ  NOT NULL,
    finished_at          TIMESTAMPTZ,
    status               VARCHAR(32)  NOT NULL,
    tickers_evaluated    INT          NOT NULL DEFAULT 0,
    signals_generated    INT          NOT NULL DEFAULT 0,
    orders_attempted     INT          NOT NULL DEFAULT 0,
    orders_placed        INT          NOT NULL DEFAULT 0,
    message              TEXT
);

CREATE INDEX idx_robinhood_crypto_auto_trade_runs_owner_started
    ON robinhood_crypto_auto_trade_runs (owner_user_id, started_at DESC);
