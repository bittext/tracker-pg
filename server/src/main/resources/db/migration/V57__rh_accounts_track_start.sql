-- RH Accounts Track window (separate from NBIS ledger cutoff on account_tracker_config).

ALTER TABLE robinhood_account_tracker_config
    ADD COLUMN IF NOT EXISTS rh_accounts_track_started_at TIMESTAMPTZ;

UPDATE robinhood_account_tracker_config
SET rh_accounts_track_started_at = TIMESTAMPTZ '2026-04-05 05:00:00+00'
WHERE rh_accounts_track_started_at IS NULL;
