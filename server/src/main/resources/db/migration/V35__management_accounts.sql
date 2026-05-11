-- Per-owner Account vault (item name + folder + username + password + authenticator key + website + notes).
-- password_enc and authenticator_key_enc are AES-256-GCM sealed when TRACKER_MANAGEMENT_ACCOUNTS_ENCRYPTION_KEY is set
-- (sealed values use the "enc1$" prefix; otherwise stored plaintext for dev).

CREATE TABLE management_accounts (
    id                     BIGSERIAL PRIMARY KEY,
    owner_user_id          BIGINT       NOT NULL REFERENCES auth_users (id),
    item_name              TEXT         NOT NULL,
    folder                 TEXT         NOT NULL DEFAULT '',
    username               TEXT         NOT NULL DEFAULT '',
    password_enc           TEXT         NOT NULL DEFAULT '',
    authenticator_key_enc  TEXT         NOT NULL DEFAULT '',
    website                TEXT         NOT NULL DEFAULT '',
    notes                  TEXT         NOT NULL DEFAULT '',
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_management_accounts_owner ON management_accounts (owner_user_id);
CREATE INDEX idx_management_accounts_owner_folder ON management_accounts (owner_user_id, folder);
