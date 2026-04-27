-- Form 1040 PDF storage (per owner + tax year). Shipped as V14 because some databases already recorded a
-- different V13 migration (checksum mismatch). If Flyway still reports version 13, remove that history row once:
--   DELETE FROM flyway_schema_history WHERE version = '13';
-- then restart so this migration can run. Uses IF NOT EXISTS so it is safe if the table already exists.

CREATE TABLE IF NOT EXISTS finance_tax_1040_returns (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT        NOT NULL REFERENCES auth_users (id),
    tax_year             INT           NOT NULL CHECK (tax_year >= 1990 AND tax_year <= 2100),
    storage_key          TEXT          NOT NULL,
    original_filename    TEXT          NOT NULL,
    content_type         TEXT,
    size_bytes           BIGINT        NOT NULL,
    extracted_text       TEXT,
    summary_json         TEXT          NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_finance_tax_1040_owner_year UNIQUE (owner_user_id, tax_year)
);

CREATE INDEX IF NOT EXISTS idx_finance_tax_1040_owner ON finance_tax_1040_returns (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_finance_tax_1040_year ON finance_tax_1040_returns (tax_year);
