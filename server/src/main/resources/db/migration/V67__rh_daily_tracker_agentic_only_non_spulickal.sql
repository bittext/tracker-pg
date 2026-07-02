-- Daily Tracker for non-spulickal users is Agentic-account-only; drop other account snapshots.

DELETE FROM robinhood_rh_daily_snapshot snap
WHERE snap.owner_user_id IN (
    SELECT id FROM auth_users WHERE lower(trim(username)) <> lower(trim('spulickal'))
)
AND (
    upper(trim(snap.account_kind)) <> 'AGENTIC'
    OR snap.account_suffix IS DISTINCT FROM (
        SELECT nullif(trim(c.agentic_account_suffix), '0000')
        FROM robinhood_account_tracker_config c
        WHERE c.owner_user_id = snap.owner_user_id
    )
);

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
