-- Sub-account linkage: multiple Tracker institutions may share one Plaid Item/token (one row per plaid account).
ALTER TABLE banking_plaid_items
    ADD COLUMN IF NOT EXISTS plaid_account_id TEXT;
