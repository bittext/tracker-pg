-- Planned stock/option interests: when the trader intended to enter, underlying price,
-- and for options also contract target cost + expiry.

CREATE TABLE robinhood_trade_interest (
    id                     BIGSERIAL PRIMARY KEY,
    owner_user_id          BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    instrument_kind        VARCHAR(16)    NOT NULL,
    symbol                 VARCHAR(32)    NOT NULL,
    planned_at             TIMESTAMPTZ    NOT NULL,
    underlying_price       NUMERIC(19, 4) NOT NULL,
    contract_target_price  NUMERIC(19, 4),
    expiry_date            DATE,
    note                   TEXT,
    status                 VARCHAR(16)    NOT NULL DEFAULT 'OPEN',
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rh_trade_interest_kind CHECK (instrument_kind IN ('STOCK', 'OPTION')),
    CONSTRAINT chk_rh_trade_interest_status CHECK (status IN ('OPEN', 'TAKEN', 'PASSED', 'EXPIRED')),
    CONSTRAINT chk_rh_trade_interest_underlying CHECK (underlying_price > 0),
    CONSTRAINT chk_rh_trade_interest_contract CHECK (
        instrument_kind = 'STOCK'
        OR (contract_target_price IS NOT NULL AND contract_target_price > 0 AND expiry_date IS NOT NULL)
    )
);

CREATE INDEX idx_rh_trade_interest_owner_planned
    ON robinhood_trade_interest (owner_user_id, planned_at DESC);

CREATE INDEX idx_rh_trade_interest_owner_status
    ON robinhood_trade_interest (owner_user_id, status);
