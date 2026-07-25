-- Cloud-backed recording audio (uploaded from Just Press Record / iCloud Drive via the browser).
ALTER TABLE management_recording_cache
    ADD COLUMN IF NOT EXISTS storage_key VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_management_recording_cache_owner_storage
    ON management_recording_cache (owner_user_id)
    WHERE storage_key IS NOT NULL;
