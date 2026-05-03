-- Case-insensitive unique username (replaces UNIQUE(username), which allowed "Bob" vs "bob").
-- Case-insensitive unique member email (non-blank only; NULL/blank allowed for multiple rows).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM auth_users
        GROUP BY LOWER(TRIM(username))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'V25: auth_users has duplicate usernames when compared case-insensitively (after trim). Resolve before migrating.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM member_profiles
        WHERE email IS NOT NULL
          AND btrim(email) <> ''
        GROUP BY LOWER(TRIM(email))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'V25: member_profiles has duplicate emails when compared case-insensitively (after trim). Resolve before migrating.';
    END IF;
END $$;

UPDATE auth_users
SET username = LOWER(TRIM(username))
WHERE username IS DISTINCT FROM LOWER(TRIM(username));

UPDATE member_profiles
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL
  AND email IS DISTINCT FROM LOWER(TRIM(email));

ALTER TABLE auth_users DROP CONSTRAINT IF EXISTS auth_users_username_key;

CREATE UNIQUE INDEX ux_auth_users_username_lower
    ON auth_users (LOWER(TRIM(username)));

DROP INDEX IF EXISTS ix_member_profiles_email_lower;

CREATE UNIQUE INDEX ux_member_profiles_email_lower
    ON member_profiles (LOWER(TRIM(email)))
    WHERE email IS NOT NULL
      AND btrim(email) <> '';
