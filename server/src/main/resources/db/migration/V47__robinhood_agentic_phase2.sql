-- Robinhood Agentic Phase 2: guarded order execution + per-user guardrails.

CREATE TABLE robinhood_agentic_settings (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL,
    require_approval     BOOLEAN        NOT NULL DEFAULT TRUE,
    max_order_notional   NUMERIC(19, 2),
    allowed_symbols      TEXT,
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_robinhood_agentic_settings_owner UNIQUE (owner_user_id)
);

CREATE INDEX idx_robinhood_agentic_settings_owner ON robinhood_agentic_settings (owner_user_id);

CREATE TABLE robinhood_agentic_orders (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL,
    status               VARCHAR(32)    NOT NULL,
    symbol               TEXT           NOT NULL,
    side                 VARCHAR(8)     NOT NULL,
    order_type           VARCHAR(16)    NOT NULL,
    quantity             NUMERIC(19, 6),
    amount               NUMERIC(19, 2),
    limit_price          NUMERIC(19, 6),
    time_in_force        VARCHAR(16),
    account_number       TEXT,
    estimated_notional   NUMERIC(19, 2),
    review_json          TEXT,
    place_json           TEXT,
    robinhood_order_id   TEXT,
    error_message        TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    reviewed_at          TIMESTAMPTZ,
    placed_at            TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_robinhood_agentic_orders_owner_created
    ON robinhood_agentic_orders (owner_user_id, created_at DESC);

CREATE INDEX idx_robinhood_agentic_orders_owner_status
    ON robinhood_agentic_orders (owner_user_id, status);
