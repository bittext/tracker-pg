-- Managed starting balance default + supplemental cash flows (e.g. Managed ••••4123 deposits not on individual CSV).

ALTER TABLE robinhood_account_tracker_config
    ALTER COLUMN managed_starting_total_value SET DEFAULT 100;

UPDATE robinhood_account_tracker_config
SET managed_starting_total_value = 100
WHERE managed_starting_total_value IS NULL;

CREATE TABLE robinhood_rh_supplemental_cash_flow (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix  VARCHAR(8)     NOT NULL,
    activity_date   DATE           NOT NULL,
    direction       VARCHAR(3)     NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    flow_category   VARCHAR(32)    NOT NULL,
    trans_code      VARCHAR(64),
    description     TEXT,
    source          VARCHAR(32)    NOT NULL DEFAULT 'Config',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rh_supplemental_cash_flow_owner_suffix
    ON robinhood_rh_supplemental_cash_flow (owner_user_id, account_suffix);

-- Managed YTD external deposit ($750) + mirrored internal $400 = $1,150 deposits / transfers in.
INSERT INTO robinhood_rh_supplemental_cash_flow (
    owner_user_id,
    account_suffix,
    activity_date,
    direction,
    amount,
    flow_category,
    trans_code,
    description,
    source
)
SELECT
    c.owner_user_id,
    COALESCE(NULLIF(TRIM(c.managed_account_suffix), ''), '4123'),
    DATE '2026-04-05',
    'IN',
    750.00,
    'EXTERNAL_IN',
    'CDEP',
    'Managed account deposit (YTD)',
    'Config'
FROM robinhood_account_tracker_config c
WHERE NOT EXISTS (
    SELECT 1
    FROM robinhood_rh_supplemental_cash_flow s
    WHERE s.owner_user_id = c.owner_user_id
      AND s.account_suffix = COALESCE(NULLIF(TRIM(c.managed_account_suffix), ''), '4123')
      AND s.flow_category = 'EXTERNAL_IN'
      AND s.amount = 750.00
);
