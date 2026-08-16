-- Individual ••••3370 YTD capital: cash start at Jan 1 2026 12:00 AM, plus interest
-- credits that were not in the imported CSV (file ended 2026-06-24).

CREATE TABLE robinhood_cash_io_year_start (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix  VARCHAR(8)     NOT NULL,
    year            INT            NOT NULL,
    start_date      DATE           NOT NULL,
    starting_cash   NUMERIC(19, 2) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_cash_io_year_start UNIQUE (owner_user_id, account_suffix, year)
);

INSERT INTO robinhood_cash_io_year_start (
    owner_user_id, account_suffix, year, start_date, starting_cash
)
SELECT u.id, '3370', 2026, DATE '2026-01-01', 211.76
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
ON CONFLICT (owner_user_id, account_suffix, year) DO UPDATE
SET start_date = EXCLUDED.start_date,
    starting_cash = EXCLUDED.starting_cash,
    updated_at = NOW();

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
SELECT u.id, v.account_suffix, v.activity_date, 'IN', v.amount, 'INTEREST', 'INT', v.description, 'Manual'
FROM auth_users u
CROSS JOIN (
    VALUES
        ('3370', DATE '2026-06-30', 63.73, 'Interest received (not in CSV)'),
        ('3370', DATE '2026-07-16', 0.77, 'Interest received (not in CSV)'),
        ('3370', DATE '2026-07-31', 2.52, 'Interest received (not in CSV)')
) AS v(account_suffix, activity_date, amount, description)
WHERE lower(u.username) = 'spulickal'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_rh_supplemental_cash_flow s
      WHERE s.owner_user_id = u.id
        AND s.account_suffix = v.account_suffix
        AND s.activity_date = v.activity_date
        AND s.flow_category = 'INTEREST'
        AND s.amount = v.amount
  );
