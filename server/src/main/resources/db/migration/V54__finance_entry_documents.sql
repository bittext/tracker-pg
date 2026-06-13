-- Finance entry documents (investments, loans, credit cards, insurance policies).

CREATE TABLE finance_entry_documents (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    entity_type         VARCHAR(32)  NOT NULL,
    entity_id           BIGINT       NOT NULL,
    storage_key         TEXT         NOT NULL,
    original_filename   TEXT         NOT NULL,
    content_type        VARCHAR(255),
    size_bytes          BIGINT       NOT NULL,
    display_name        VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_entry_documents_owner_entity
    ON finance_entry_documents (owner_user_id, entity_type, entity_id);
