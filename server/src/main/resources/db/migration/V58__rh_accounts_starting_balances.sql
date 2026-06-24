-- Starting balances at RH Accounts Track cutoff + managed account suffix.

ALTER TABLE robinhood_account_tracker_config
    ADD COLUMN IF NOT EXISTS individual_starting_total_value NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS agentic_starting_total_value NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS managed_account_suffix VARCHAR(8),
    ADD COLUMN IF NOT EXISTS managed_starting_total_value NUMERIC(19, 2);

UPDATE robinhood_account_tracker_config
SET managed_account_suffix = COALESCE(managed_account_suffix, '4123'),
    agentic_starting_total_value = COALESCE(agentic_starting_total_value, 0)
WHERE managed_account_suffix IS NULL OR agentic_starting_total_value IS NULL;
