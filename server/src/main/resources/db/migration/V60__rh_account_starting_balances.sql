-- Per-suffix starting portfolio totals at RH Accounts Track cutoff (Apr 5 2026 Central).

CREATE TABLE robinhood_rh_account_starting_balance (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix       VARCHAR(8)     NOT NULL,
    starting_total_value NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_account_starting_balance_owner_suffix UNIQUE (owner_user_id, account_suffix)
);

CREATE INDEX idx_rh_account_starting_balance_owner
    ON robinhood_rh_account_starting_balance (owner_user_id);

INSERT INTO robinhood_rh_account_starting_balance (owner_user_id, account_suffix, starting_total_value, created_at, updated_at)
SELECT c.owner_user_id, TRIM(c.individual_account_suffix), COALESCE(c.individual_starting_total_value, 0), NOW(), NOW()
FROM robinhood_account_tracker_config c
ON CONFLICT (owner_user_id, account_suffix) DO NOTHING;

INSERT INTO robinhood_rh_account_starting_balance (owner_user_id, account_suffix, starting_total_value, created_at, updated_at)
SELECT c.owner_user_id, TRIM(c.agentic_account_suffix), COALESCE(c.agentic_starting_total_value, 0), NOW(), NOW()
FROM robinhood_account_tracker_config c
ON CONFLICT (owner_user_id, account_suffix) DO NOTHING;

INSERT INTO robinhood_rh_account_starting_balance (owner_user_id, account_suffix, starting_total_value, created_at, updated_at)
SELECT
    c.owner_user_id,
    TRIM(c.managed_account_suffix),
    COALESCE(c.managed_starting_total_value, 0),
    NOW(),
    NOW()
FROM robinhood_account_tracker_config c
WHERE c.managed_account_suffix IS NOT NULL
  AND TRIM(c.managed_account_suffix) <> ''
ON CONFLICT (owner_user_id, account_suffix) DO NOTHING;
