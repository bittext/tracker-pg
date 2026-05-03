-- Accounts without a complete member_profiles row (same required fields as the app) must finish onboarding:
-- Admin → My profile, then member ID, then Welcome. Existing logins skip the credentials wizard.

UPDATE auth_users u
SET
    onboarding_completed_at = NULL,
    credentials_step_completed_at = COALESCE(u.credentials_step_completed_at, NOW())
WHERE NOT EXISTS (
    SELECT 1
    FROM member_profiles p
    WHERE p.user_id = u.id
      AND p.first_name IS NOT NULL
      AND btrim(p.first_name) <> ''
      AND p.last_name IS NOT NULL
      AND btrim(p.last_name) <> ''
      AND p.date_of_birth IS NOT NULL
      AND p.email IS NOT NULL
      AND btrim(p.email) <> ''
      AND p.phone_country_code IS NOT NULL
      AND btrim(p.phone_country_code) <> ''
      AND p.phone_national_number IS NOT NULL
      AND btrim(p.phone_national_number) <> ''
      AND p.address_line1 IS NOT NULL
      AND btrim(p.address_line1) <> ''
      AND p.city IS NOT NULL
      AND btrim(p.city) <> ''
      AND p.state_region IS NOT NULL
      AND btrim(p.state_region) <> ''
      AND p.postal_code IS NOT NULL
      AND btrim(p.postal_code) <> ''
);
