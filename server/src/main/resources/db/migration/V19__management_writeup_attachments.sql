-- File attachments for management write-ups (same blob store pattern as month notes).

CREATE TABLE IF NOT EXISTS management_writeup_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    writeup_id         BIGINT        NOT NULL REFERENCES management_writeups (id) ON DELETE CASCADE,
    storage_key        TEXT          NOT NULL,
    original_filename  TEXT          NOT NULL,
    content_type       TEXT,
    size_bytes         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_management_writeup_att_writeup
    ON management_writeup_attachments (writeup_id);
