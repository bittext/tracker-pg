-- nisha Daily Tracker is nisha-agentic only (4190, 7581); drop pulickal-agentic contamination.

DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
)
AND snap.account_suffix NOT IN ('4190', '7581');

DELETE FROM robinhood_rh_account_starting_balance b
WHERE b.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
)
AND b.account_suffix IN ('3370', '3550', '4123', '8696', '0440', '2835');

DELETE FROM robinhood_rh_supplemental_cash_flow s
WHERE s.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
)
AND s.account_suffix IN ('3370', '3550', '4123', '8696', '0440', '2835');

DELETE FROM robinhood_agentic_positions p
WHERE p.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
)
AND (
    p.account_number LIKE '%3370'
    OR p.account_number LIKE '%3550'
    OR p.account_number LIKE '%4123'
    OR p.account_number LIKE '%8696'
    OR p.account_number LIKE '%0440'
    OR p.account_number LIKE '%2835'
);

UPDATE robinhood_account_tracker_config c
SET individual_account_suffix = '0000',
    agentic_account_suffix = '0000',
    managed_account_suffix = NULL,
    excluded_account_suffixes = '',
    updated_at = NOW()
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
)
AND (
    c.individual_account_suffix IN ('3370', '3550', '4123', '8696', '0440', '2835')
    OR c.agentic_account_suffix IN ('3370', '3550', '4123', '8696', '0440', '2835')
    OR c.managed_account_suffix IN ('3370', '3550', '4123', '8696', '0440', '2835')
);
