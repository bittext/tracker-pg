-- Friday Sep 11 2026 Individual transfers from the Robinhood Transfers screen.
-- CSV import is stale through 2026-08-31, so these never landed in robinhood_transactions.

INSERT INTO robinhood_account_cash_io (
    owner_user_id, account_suffix, activity_date, direction, amount, note
)
SELECT u.id, '3370', DATE '2026-09-11', 'OUT', 6000.00,
       'Withdrawal from Individual to TOTAL CHECKING (pending ACH)'
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_account_cash_io e
      WHERE e.owner_user_id = u.id
        AND e.account_suffix = '3370'
        AND e.activity_date = DATE '2026-09-11'
        AND e.direction = 'OUT'
        AND e.amount = 6000.00
  );

INSERT INTO robinhood_account_cash_io (
    owner_user_id, account_suffix, activity_date, direction, amount, note
)
SELECT u.id, '3370', DATE '2026-09-11', 'OUT', 7690.94,
       'Robinhood Credit Card balance payment'
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_account_cash_io e
      WHERE e.owner_user_id = u.id
        AND e.account_suffix = '3370'
        AND e.activity_date = DATE '2026-09-11'
        AND e.direction = 'OUT'
        AND e.amount = 7690.94
  );

INSERT INTO robinhood_account_cash_io (
    owner_user_id, account_suffix, activity_date, direction, amount, note
)
SELECT u.id, '3370', DATE '2026-09-11', 'OUT', 15000.00,
       'RH Banking a/c from Individual'
FROM auth_users u
WHERE lower(u.username) = 'spulickal'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_account_cash_io e
      WHERE e.owner_user_id = u.id
        AND e.account_suffix = '3370'
        AND e.activity_date = DATE '2026-09-11'
        AND e.direction = 'OUT'
        AND e.amount = 15000.00
  );
