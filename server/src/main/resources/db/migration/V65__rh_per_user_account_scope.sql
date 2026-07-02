-- Stop applying spulickal's default RH account suffixes to users who do not own those accounts.

UPDATE robinhood_account_tracker_config c
SET individual_account_suffix = '0000',
    agentic_account_suffix = '0000',
    managed_account_suffix = NULL,
    excluded_account_suffixes = ''
WHERE (c.individual_account_suffix IN ('3370', '0000') AND c.agentic_account_suffix IN ('3550', '0000'))
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_agentic_positions p
      WHERE p.owner_user_id = c.owner_user_id
        AND (p.account_number LIKE '%3370' OR p.account_number LIKE '%3550')
  )
  AND EXISTS (
      SELECT 1
      FROM robinhood_agentic_positions p2
      WHERE p2.owner_user_id = c.owner_user_id
  );

-- Drop managed-account supplemental flows when that user has no ••••4123 holdings.
DELETE FROM robinhood_rh_supplemental_cash_flow s
WHERE s.account_suffix = '4123'
  AND NOT EXISTS (
      SELECT 1
      FROM robinhood_agentic_positions p
      WHERE p.owner_user_id = s.owner_user_id
        AND p.account_number LIKE '%4123'
  );

-- Hide stale Daily Tracker rows for account suffixes this owner does not hold.
DELETE FROM robinhood_rh_daily_snapshot snap
WHERE EXISTS (
    SELECT 1
    FROM robinhood_agentic_positions p
    WHERE p.owner_user_id = snap.owner_user_id
)
AND NOT EXISTS (
    SELECT 1
    FROM robinhood_agentic_positions p
    WHERE p.owner_user_id = snap.owner_user_id
      AND p.account_number LIKE '%' || snap.account_suffix
);
