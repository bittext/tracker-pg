-- Robinhood Agentic Trading MCP: per-user OAuth tokens and synced read snapshots (Phase 1).

CREATE TABLE robinhood_agentic_connections (
    id                 BIGSERIAL PRIMARY KEY,
    owner_user_id      BIGINT       NOT NULL,
    access_token       TEXT         NOT NULL,
    refresh_token      TEXT,
    agentic_account_number TEXT,
    agentic_nickname   TEXT,
    connected_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_sync_at       TIMESTAMPTZ,
    last_sync_status   VARCHAR(32),
    last_sync_message  TEXT,
    portfolio_json     TEXT,
    CONSTRAINT uq_robinhood_agentic_connections_owner UNIQUE (owner_user_id)
);

CREATE INDEX idx_robinhood_agentic_connections_owner ON robinhood_agentic_connections (owner_user_id);

CREATE TABLE robinhood_agentic_positions (
    id                 BIGSERIAL PRIMARY KEY,
    owner_user_id      BIGINT       NOT NULL,
    account_number     TEXT         NOT NULL,
    symbol             TEXT         NOT NULL,
    quantity           NUMERIC(19, 6),
    average_buy_price  NUMERIC(19, 6),
    market_value       NUMERIC(19, 2),
    synced_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_robinhood_agentic_positions_owner_acct_sym
        UNIQUE (owner_user_id, account_number, symbol)
);

CREATE INDEX idx_robinhood_agentic_positions_owner ON robinhood_agentic_positions (owner_user_id);

CREATE TABLE robinhood_agentic_sync_log (
    id                 BIGSERIAL PRIMARY KEY,
    owner_user_id      BIGINT       NOT NULL,
    started_at         TIMESTAMPTZ  NOT NULL,
    finished_at        TIMESTAMPTZ,
    status             VARCHAR(32)  NOT NULL,
    message            TEXT,
    accounts_synced    INT
);

CREATE INDEX idx_robinhood_agentic_sync_log_owner_started
    ON robinhood_agentic_sync_log (owner_user_id, started_at DESC);
