-- Plaid Item + access token per Tracker banking institution (one Plaid connection per institution row).
CREATE TABLE IF NOT EXISTS banking_plaid_items (
    id               BIGSERIAL PRIMARY KEY,
    owner_user_id    BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    institution_id   BIGINT        NOT NULL REFERENCES banking_institutions (id) ON DELETE CASCADE,
    item_id          TEXT          NOT NULL,
    access_token     TEXT          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_banking_plaid_item_owner_institution UNIQUE (owner_user_id, institution_id)
);

CREATE INDEX IF NOT EXISTS idx_banking_plaid_item_institution ON banking_plaid_items (institution_id);
