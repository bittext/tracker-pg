-- Remove Daily Tracker / RH track rows that belong to spulickal's accounts but were stored under other users.

-- Spulickal-only Robinhood account suffixes (individual ••••3370, agentic ••••3550, managed ••••4123, excluded ••••0440/2835).
DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND snap.account_suffix IN ('3370', '3550', '4123', '0440', '2835');

-- Nightly scheduled captures apply only to spulickal.
DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.capture_kind = 'SCHEDULED'
  AND snap.owner_user_id IN (
      SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
  );

-- Drop any remaining snapshot suffix this owner does not hold (after Agentic sync).
DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND EXISTS (
    SELECT 1 FROM robinhood_agentic_positions p WHERE p.owner_user_id = snap.owner_user_id
)
AND NOT EXISTS (
    SELECT 1
    FROM robinhood_agentic_positions p
    WHERE p.owner_user_id = snap.owner_user_id
      AND p.account_number LIKE '%' || snap.account_suffix
);

DELETE FROM robinhood_rh_account_starting_balance b
WHERE b.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND b.account_suffix IN ('3370', '3550', '4123', '0440', '2835');

DELETE FROM robinhood_rh_account_starting_balance b
WHERE b.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND EXISTS (
    SELECT 1 FROM robinhood_agentic_positions p WHERE p.owner_user_id = b.owner_user_id
)
AND NOT EXISTS (
    SELECT 1
    FROM robinhood_agentic_positions p
    WHERE p.owner_user_id = b.owner_user_id
      AND p.account_number LIKE '%' || b.account_suffix
);

DELETE FROM robinhood_rh_supplemental_cash_flow s
WHERE s.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND s.account_suffix IN ('3370', '3550', '4123', '0440', '2835');

DELETE FROM robinhood_rh_daily_day_note n
WHERE n.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND NOT EXISTS (
    SELECT 1
    FROM robinhood_rh_daily_snapshot s
    WHERE s.owner_user_id = n.owner_user_id
      AND s.snapshot_date = n.snapshot_date
);

UPDATE robinhood_account_tracker_config c
SET individual_account_suffix = '0000'
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND c.individual_account_suffix IN ('3370', '3550', '4123', '0440', '2835');

UPDATE robinhood_account_tracker_config c
SET agentic_account_suffix = '0000'
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND c.agentic_account_suffix IN ('3370', '3550', '4123', '0440', '2835');

UPDATE robinhood_account_tracker_config c
SET managed_account_suffix = NULL
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND c.managed_account_suffix IN ('3370', '3550', '4123', '0440', '2835');

UPDATE robinhood_account_tracker_config c
SET excluded_account_suffixes = ''
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND TRIM(c.excluded_account_suffixes) IN ('0440,2835', '2835,0440');
