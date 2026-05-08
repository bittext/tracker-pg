-- Timestamp when the member acknowledged the in-app Privacy policy for financial data / Plaid (required before Link).
ALTER TABLE member_profiles
    ADD COLUMN IF NOT EXISTS plaid_financial_data_notice_accepted_at TIMESTAMPTZ;
