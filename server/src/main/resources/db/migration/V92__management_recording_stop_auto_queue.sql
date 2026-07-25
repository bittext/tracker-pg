-- Stop automatic background transcription queue: idle out any pending/processing work.
UPDATE management_recording_cache
SET processing_status = CASE
        WHEN transcript IS NOT NULL AND btrim(transcript) <> '' THEN 'READY'
        ELSE 'IDLE'
    END,
    processing_error = NULL,
    processing_started_at = NULL,
    processing_completed_at = CASE
        WHEN transcript IS NOT NULL AND btrim(transcript) <> '' THEN COALESCE(processing_completed_at, NOW())
        ELSE NULL
    END
WHERE processing_status IN ('PENDING', 'PROCESSING');

ALTER TABLE management_recording_cache
    ALTER COLUMN processing_status SET DEFAULT 'IDLE';
