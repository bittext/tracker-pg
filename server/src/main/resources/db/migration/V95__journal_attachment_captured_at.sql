-- Image capture time (EXIF / filename) so Journal attachments list oldest-first by photo time.
ALTER TABLE journal_attachments
    ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_journal_attachments_entry_captured
    ON journal_attachments (entry_id, COALESCE(captured_at, created_at) ASC);
