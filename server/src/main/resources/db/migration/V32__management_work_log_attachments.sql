-- File attachments for day-scoped work log entries (same storage pattern as month notes / write-ups).

CREATE TABLE management_work_log_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    entry_id           BIGINT        NOT NULL REFERENCES management_work_log_entries (id) ON DELETE CASCADE,
    storage_key        TEXT          NOT NULL,
    original_filename  TEXT          NOT NULL,
    content_type       TEXT,
    size_bytes         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_management_work_log_att_entry ON management_work_log_attachments (entry_id);
