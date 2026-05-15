-- Management → Documents: member-scoped file vault (metadata + blob storage key).
CREATE TABLE management_documents (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    display_name VARCHAR(512) NOT NULL,
    doc_type VARCHAR(64) NOT NULL,
    original_filename VARCHAR(512),
    content_type VARCHAR(255),
    byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
    storage_key VARCHAR(768) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_management_documents_owner_created
    ON management_documents (owner_user_id, created_at DESC);
