-- Durable background transcription/summary queue.
ALTER TABLE management_recording_cache
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS processing_error TEXT,
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS processing_completed_at TIMESTAMPTZ;

-- Re-run every existing uploaded recording once after this feature deploys.
UPDATE management_recording_cache
SET processing_status = 'PENDING',
    processing_error = NULL,
    processing_started_at = NULL,
    processing_completed_at = NULL
WHERE storage_key IS NOT NULL
  AND storage_key <> '';

CREATE INDEX IF NOT EXISTS idx_management_recording_processing
    ON management_recording_cache (processing_status, updated_at);
