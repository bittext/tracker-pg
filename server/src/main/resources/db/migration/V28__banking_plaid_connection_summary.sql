-- Plaid connection metadata for UI (institution/account labels); not used for auth.
ALTER TABLE banking_plaid_items
    ADD COLUMN IF NOT EXISTS plaid_institution_id TEXT;

ALTER TABLE banking_plaid_items
    ADD COLUMN IF NOT EXISTS connection_summary TEXT;
