-- Robinhood Agentic Phase 1: option positions from MCP get_option_positions.

ALTER TABLE robinhood_agentic_positions
    ADD COLUMN position_type     VARCHAR(16)  NOT NULL DEFAULT 'equity',
    ADD COLUMN position_key      TEXT,
    ADD COLUMN chain_symbol      TEXT,
    ADD COLUMN option_type       VARCHAR(8),
    ADD COLUMN strike_price      NUMERIC(19, 6),
    ADD COLUMN expiration_date   DATE;

UPDATE robinhood_agentic_positions
SET position_key = UPPER(symbol)
WHERE position_key IS NULL;

ALTER TABLE robinhood_agentic_positions
    ALTER COLUMN position_key SET NOT NULL;

ALTER TABLE robinhood_agentic_positions
    DROP CONSTRAINT uq_robinhood_agentic_positions_owner_acct_sym;

ALTER TABLE robinhood_agentic_positions
    ADD CONSTRAINT uq_robinhood_agentic_positions_owner_acct_key
        UNIQUE (owner_user_id, account_number, position_key);

CREATE INDEX idx_robinhood_agentic_positions_type
    ON robinhood_agentic_positions (owner_user_id, position_type);
