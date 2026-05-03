-- Repair auth_users onboarding columns when V20 was applied from an older script revision (Flyway will not re-run V20).
-- Safe on fresh databases: IF NOT EXISTS is a no-op when V20 already added these.

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credentials_step_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS member_public_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_users_member_public_id
    ON auth_users (member_public_id)
    WHERE member_public_id IS NOT NULL;
