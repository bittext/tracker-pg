-- Robinhood Agentic: cached recent equity orders from MCP get_equity_orders (last N per sync).

CREATE TABLE robinhood_agentic_synced_orders (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL,
    account_number       TEXT           NOT NULL,
    robinhood_order_id   TEXT           NOT NULL,
    symbol               TEXT           NOT NULL,
    side                 VARCHAR(8),
    order_type           VARCHAR(16),
    quantity             NUMERIC(19, 6),
    limit_price          NUMERIC(19, 6),
    average_price        NUMERIC(19, 6),
    state                VARCHAR(32),
    created_at_rh        TIMESTAMPTZ,
    updated_at_rh        TIMESTAMPTZ,
    synced_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_robinhood_agentic_synced_orders_owner_acct_rh
        UNIQUE (owner_user_id, account_number, robinhood_order_id)
);

CREATE INDEX idx_robinhood_agentic_synced_orders_owner_updated
    ON robinhood_agentic_synced_orders (owner_user_id, updated_at_rh DESC NULLS LAST);
