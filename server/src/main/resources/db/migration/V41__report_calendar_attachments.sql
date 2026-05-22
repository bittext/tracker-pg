-- File attachments for Management calendar entries (report_calendar_entries).

CREATE TABLE IF NOT EXISTS report_calendar_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    entry_id           BIGINT        NOT NULL REFERENCES report_calendar_entries (id) ON DELETE CASCADE,
    storage_key        TEXT          NOT NULL,
    original_filename  TEXT          NOT NULL,
    content_type       TEXT,
    size_bytes         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_calendar_att_entry
    ON report_calendar_attachments (entry_id);
