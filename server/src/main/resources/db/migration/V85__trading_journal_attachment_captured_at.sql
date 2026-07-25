-- Capture/taken timestamp for journal image attachments (distinct from upload created_at).

ALTER TABLE trading_journal_attachment
    ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_trading_journal_attachment_entry_captured
    ON trading_journal_attachment (entry_id, COALESCE(captured_at, created_at) ASC);
