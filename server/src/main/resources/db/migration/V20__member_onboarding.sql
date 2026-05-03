-- Member onboarding: credentials step, profile (1:1), public member id, completion timestamp.
-- Existing users are marked complete so only new accounts (null onboarding) go through the flow.

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credentials_step_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS member_public_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS ux_auth_users_member_public_id
    ON auth_users (member_public_id)
    WHERE member_public_id IS NOT NULL;

UPDATE auth_users
SET onboarding_completed_at = COALESCE(onboarding_completed_at, NOW())
WHERE onboarding_completed_at IS NULL;

CREATE TABLE IF NOT EXISTS member_profiles (
    user_id                 BIGINT PRIMARY KEY REFERENCES auth_users (id) ON DELETE CASCADE,
    first_name              VARCHAR(100),
    middle_name             VARCHAR(100),
    last_name               VARCHAR(100),
    date_of_birth           DATE,
    email                   VARCHAR(320),
    phone_country_code      VARCHAR(8),
    phone_national_number   VARCHAR(32),
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(120),
    state_region            VARCHAR(64),
    postal_code             VARCHAR(20),
    validated_postal_code   VARCHAR(20),
    validated_city          VARCHAR(120),
    validated_state_region  VARCHAR(64),
    address_use_validated_suggestion BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_email_opt_in  BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_sms_opt_in    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_member_profiles_email_lower ON member_profiles (LOWER(email));
