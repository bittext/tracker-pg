-- Non-spulickal Daily Tracker uses every account from the user's own Agentic sync (no auto-exclusions).

UPDATE robinhood_account_tracker_config c
SET excluded_account_suffixes = '',
    updated_at = NOW()
WHERE c.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND btrim(c.excluded_account_suffixes) <> '';
