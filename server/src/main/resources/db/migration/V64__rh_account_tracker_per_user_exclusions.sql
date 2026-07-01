-- Per-user Daily Tracker / RH Accounts Track exclusions (was global in application.yml for spulickal only).

ALTER TABLE robinhood_account_tracker_config
    ADD COLUMN excluded_account_suffixes TEXT NOT NULL DEFAULT '';

-- Preserve spulickal's prior global exclusions (••••0440, ••••2835) on his Agentic ••••3550 config.
UPDATE robinhood_account_tracker_config
SET excluded_account_suffixes = '0440,2835'
WHERE agentic_account_suffix = '3550'
  AND TRIM(excluded_account_suffixes) = '';
