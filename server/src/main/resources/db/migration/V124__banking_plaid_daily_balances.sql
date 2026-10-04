-- Per-institution daily Plaid balance capture, used as the Due opening balance on the 1st.

ALTER TABLE banking_plaid_items
    ADD COLUMN IF NOT EXISTS daily_balance_sync BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS banking_plaid_balance_snapshots (
    id                BIGSERIAL PRIMARY KEY,
    owner_user_id     BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    institution_id    BIGINT         NOT NULL REFERENCES banking_institutions (id) ON DELETE CASCADE,
    snapshot_date     DATE           NOT NULL,
    plaid_account_id  TEXT           NOT NULL,
    account_label     TEXT           NOT NULL,
    current_balance   NUMERIC(19, 2) NOT NULL,
    available_balance NUMERIC(19, 2),
    iso_currency      VARCHAR(8),
    captured_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plaid_balance_owner_inst_day_acct
        UNIQUE (owner_user_id, institution_id, snapshot_date, plaid_account_id)
);

CREATE INDEX IF NOT EXISTS idx_plaid_balance_owner_date
    ON banking_plaid_balance_snapshots (owner_user_id, snapshot_date);
