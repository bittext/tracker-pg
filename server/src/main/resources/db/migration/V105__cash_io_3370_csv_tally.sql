-- Tally Individual ••••3370 Inputs/Outputs with the latest imported CSV.
-- 1) Apr 14 ACH Deposit $200 is in robinhood_transactions but was missing from the ledger.
-- 2) Jun 12 notes were swapped: CSV ITRF −$1,000 is the outflow to Agentic; the same-day
--    $1,000 in is from Robinhood Banking (not in the brokerage CSV).
-- 3) Record the Agentic ••••3550 inbound leg of that ITRF so the transfer balances.

INSERT INTO robinhood_account_cash_io (
    owner_user_id, account_suffix, activity_date, direction, amount, note
)
SELECT u.id, '3370', DATE '2026-04-14', 'IN', 200.00, 'ACH Deposit (CSV)'
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_account_cash_io e
      WHERE e.owner_user_id = u.id
        AND e.account_suffix = '3370'
        AND e.activity_date = DATE '2026-04-14'
        AND e.direction = 'IN'
        AND e.amount = 200.00
  );

UPDATE robinhood_account_cash_io e
SET note = 'to Agentic',
    updated_at = NOW()
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND e.owner_user_id = u.id
  AND e.account_suffix = '3370'
  AND e.activity_date = DATE '2026-06-12'
  AND e.direction = 'OUT'
  AND e.amount = 1000.00
  AND e.note = 'from Robinhood Banking';

UPDATE robinhood_account_cash_io e
SET note = 'from Robinhood Banking',
    updated_at = NOW()
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND e.owner_user_id = u.id
  AND e.account_suffix = '3370'
  AND e.activity_date = DATE '2026-06-12'
  AND e.direction = 'IN'
  AND e.amount = 1000.00
  AND e.note = 'to Agentic';

INSERT INTO robinhood_account_cash_io (
    owner_user_id, account_suffix, activity_date, direction, amount, note
)
SELECT u.id, '3550', DATE '2026-06-12', 'IN', 1000.00, 'from Individual'
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_account_cash_io e
      WHERE e.owner_user_id = u.id
        AND e.account_suffix = '3550'
        AND e.activity_date = DATE '2026-06-12'
        AND e.direction = 'IN'
        AND e.amount = 1000.00
  );
