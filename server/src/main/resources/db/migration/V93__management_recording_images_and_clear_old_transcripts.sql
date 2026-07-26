-- Photos attached to a recording (preview + gallery in Management → Recordings).
CREATE TABLE IF NOT EXISTS management_recording_images (
    id                BIGSERIAL PRIMARY KEY,
    recording_id      BIGINT NOT NULL REFERENCES management_recording_cache (id) ON DELETE CASCADE,
    owner_user_id     BIGINT NOT NULL,
    storage_key       TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    content_type      TEXT,
    size_bytes        BIGINT NOT NULL DEFAULT 0,
    sort_order        INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_management_recording_images_recording
    ON management_recording_images (recording_id, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_management_recording_images_owner
    ON management_recording_images (owner_user_id);

-- Clear pre-timed / non-segment transcripts so they can be rebuilt with the new format.
-- Keep rows that already have timed startSeconds segments.
UPDATE management_recording_cache
SET transcript = NULL,
    transcript_source = NULL,
    transcript_segments_json = NULL,
    transcribed_at = NULL,
    summary = NULL,
    summarized_at = NULL,
    processing_status = CASE
        WHEN storage_key IS NOT NULL AND TRIM(storage_key) <> '' THEN 'PENDING'
        ELSE 'IDLE'
    END,
    processing_error = NULL,
    processing_started_at = NULL,
    processing_completed_at = NULL,
    updated_at = NOW()
WHERE transcript IS NOT NULL
  AND TRIM(transcript) <> ''
  AND (
        transcript_segments_json IS NULL
        OR TRIM(transcript_segments_json) = ''
        OR TRIM(transcript_segments_json) = '[]'
        OR transcript_segments_json NOT LIKE '%"startSeconds"%'
      );
