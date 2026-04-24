-- Delete every robinhood_transactions row whose activity_date falls in calendar year 2026 (all users).

DELETE FROM robinhood_transactions
WHERE
    activity_date IS NOT NULL
    AND activity_date >= TIMESTAMP '2026-01-01 00:00:00'
    AND activity_date < TIMESTAMP '2027-01-01 00:00:00';
