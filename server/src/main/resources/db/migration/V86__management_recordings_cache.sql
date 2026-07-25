-- Cached transcripts / AI summaries for Just Press Record files (keyed by relative path under configured root).
CREATE TABLE management_recording_cache (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    relative_path VARCHAR(1024) NOT NULL,
    display_name VARCHAR(512) NOT NULL,
    recorded_day DATE,
    file_size_bytes BIGINT,
    transcript TEXT,
    transcript_source VARCHAR(64),
    summary TEXT,
    transcribed_at TIMESTAMPTZ,
    summarized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_management_recording_cache_owner_path UNIQUE (owner_user_id, relative_path)
);

CREATE INDEX idx_management_recording_cache_owner_day
    ON management_recording_cache (owner_user_id, recorded_day DESC);

CREATE INDEX idx_management_recording_cache_owner_updated
    ON management_recording_cache (owner_user_id, updated_at DESC);
