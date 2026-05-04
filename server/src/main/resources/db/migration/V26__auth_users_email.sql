-- Account email on auth_users (admin provisioning, support lookup). Distinct from member_profiles.email until profile is saved.
-- Same normalization rules as member_profiles: unique when non-blank (case-insensitive, trimmed).

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS email VARCHAR(320);

CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_users_email_lower
    ON auth_users (LOWER(TRIM(email)))
    WHERE email IS NOT NULL
      AND btrim(email) <> '';
