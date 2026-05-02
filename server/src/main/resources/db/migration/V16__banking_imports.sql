-- Banking institutions and imports (per owner). Files stored on disk under tracker.finance.banking.import-directory.

CREATE TABLE IF NOT EXISTS banking_institutions (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    name            TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_banking_institution_owner_name UNIQUE (owner_user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_banking_institutions_owner ON banking_institutions (owner_user_id);

CREATE TABLE IF NOT EXISTS banking_import_files (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT        NOT NULL REFERENCES auth_users (id),
    institution_id       BIGINT        NOT NULL REFERENCES banking_institutions (id) ON DELETE CASCADE,
    file_kind            VARCHAR(16)   NOT NULL CHECK (file_kind IN ('DATA', 'PDF')),
    original_filename    TEXT          NOT NULL,
    content_type         TEXT,
    sha256_hex           CHAR(64)      NOT NULL,
    stored_relative_path TEXT          NOT NULL,
    size_bytes           BIGINT        NOT NULL,
    skipped_duplicate_file BOOLEAN   NOT NULL DEFAULT FALSE,
    rows_inserted        INT           NOT NULL DEFAULT 0,
    rows_skipped_duplicate INT         NOT NULL DEFAULT 0,
    parse_note           TEXT,
    created_at           TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_banking_import_files_owner ON banking_import_files (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_banking_import_files_institution ON banking_import_files (institution_id);
CREATE INDEX IF NOT EXISTS idx_banking_import_files_owner_sha ON banking_import_files (owner_user_id, sha256_hex);

CREATE TABLE IF NOT EXISTS banking_transactions (
    id               BIGSERIAL PRIMARY KEY,
    owner_user_id    BIGINT        NOT NULL REFERENCES auth_users (id),
    institution_id   BIGINT        NOT NULL REFERENCES banking_institutions (id) ON DELETE CASCADE,
    import_file_id   BIGINT        NOT NULL REFERENCES banking_import_files (id) ON DELETE CASCADE,
    txn_date         DATE          NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    description      TEXT          NOT NULL DEFAULT '',
    dedupe_hash      CHAR(64)      NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_banking_txn_owner_dedupe UNIQUE (owner_user_id, dedupe_hash)
);

CREATE INDEX IF NOT EXISTS idx_banking_txn_owner_date ON banking_transactions (owner_user_id, txn_date);
CREATE INDEX IF NOT EXISTS idx_banking_txn_institution_date ON banking_transactions (institution_id, txn_date);
