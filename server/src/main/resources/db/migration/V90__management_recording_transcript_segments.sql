-- Timed speaker/segment turns for recordings transcript UI (seek + follow-playback).
ALTER TABLE management_recording_cache
    ADD COLUMN IF NOT EXISTS transcript_segments_json TEXT;
