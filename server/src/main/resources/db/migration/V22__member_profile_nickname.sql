-- Optional display name for social contexts (distinct from legal name on profile).
ALTER TABLE member_profiles
    ADD COLUMN IF NOT EXISTS nickname VARCHAR(80);
