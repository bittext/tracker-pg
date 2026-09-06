ALTER TABLE robinhood_agentic_orders
    ADD COLUMN IF NOT EXISTS asset_class VARCHAR(16) NOT NULL DEFAULT 'equity';

ALTER TABLE robinhood_agentic_orders
    ADD COLUMN IF NOT EXISTS sell_all BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE robinhood_agentic_orders
    ALTER COLUMN quantity TYPE NUMERIC(28, 12);
