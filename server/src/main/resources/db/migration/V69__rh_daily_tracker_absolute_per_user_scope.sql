-- Absolute Daily Tracker scope: spulickal = pulickal-agentic allowlist; nisha = nisha-agentic all owned; others disabled.

UPDATE robinhood_account_tracker_config c
SET excluded_account_suffixes = '0440,2835',
    updated_at = NOW()
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'spulickal'
)
AND coalesce(btrim(c.excluded_account_suffixes), '') IS DISTINCT FROM '0440,2835';

DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'spulickal'
)
AND snap.account_suffix NOT IN ('3370', '3550', '4123', '8696');

DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) = 'nisha'
)
AND snap.account_suffix NOT IN ('4190', '7581');

DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id NOT IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) IN ('spulickal', 'nisha')
);

DELETE FROM robinhood_rh_daily_day_note note
WHERE note.owner_user_id NOT IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) IN ('spulickal', 'nisha')
);
