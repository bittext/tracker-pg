-- Robinhood Agentic Banking MCP: per-user OAuth tokens and Agentic Credit Card sync snapshots.

CREATE TABLE robinhood_agentic_banking_connections (
    id                    BIGSERIAL PRIMARY KEY,
    owner_user_id         BIGINT       NOT NULL,
    access_token          TEXT         NOT NULL,
    refresh_token         TEXT,
    card_last_four        VARCHAR(4),
    card_status           VARCHAR(32),
    activation_status     VARCHAR(32),
    monthly_limit_micro   BIGINT,
    total_spend_micro     BIGINT,
    available_balance_micro BIGINT,
    connected_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_sync_at          TIMESTAMPTZ,
    last_sync_status      VARCHAR(32),
    last_sync_message     TEXT,
    snapshot_json         TEXT,
    CONSTRAINT uq_robinhood_agentic_banking_connections_owner UNIQUE (owner_user_id)
);

CREATE INDEX idx_robinhood_agentic_banking_connections_owner
    ON robinhood_agentic_banking_connections (owner_user_id);

CREATE TABLE robinhood_agentic_banking_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL,
    external_id         TEXT         NOT NULL,
    merchant_name       TEXT,
    description         TEXT,
    amount_micro        BIGINT,
    transaction_status  VARCHAR(32),
    transaction_at      TIMESTAMPTZ,
    synced_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_robinhood_agentic_banking_txn_owner_ext UNIQUE (owner_user_id, external_id)
);

CREATE INDEX idx_robinhood_agentic_banking_transactions_owner_at
    ON robinhood_agentic_banking_transactions (owner_user_id, transaction_at DESC);
