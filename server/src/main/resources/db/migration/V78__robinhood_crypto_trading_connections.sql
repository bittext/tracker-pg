-- Robinhood Crypto Trading API credentials (Ed25519 + API key), separate from Agentic MCP.

CREATE TABLE robinhood_crypto_trading_connections (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    api_key_enc         TEXT         NOT NULL,
    private_key_enc     TEXT         NOT NULL,
    account_number      VARCHAR(64),
    connected_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_sync_at        TIMESTAMPTZ,
    last_sync_status    VARCHAR(32),
    last_sync_message   TEXT,
    holdings_json       TEXT,
    CONSTRAINT uq_robinhood_crypto_trading_connections_owner UNIQUE (owner_user_id)
);

CREATE INDEX idx_robinhood_crypto_trading_connections_owner
    ON robinhood_crypto_trading_connections (owner_user_id);
