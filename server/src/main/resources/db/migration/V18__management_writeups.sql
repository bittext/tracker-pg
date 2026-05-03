-- Year-scoped write-ups (reports / reviews) per user — Management → Write-up.

CREATE TABLE IF NOT EXISTS management_writeups (
    id               BIGSERIAL PRIMARY KEY,
    owner_user_id    BIGINT        NOT NULL REFERENCES auth_users (id),
    year             INT           NOT NULL CHECK (year >= 1970 AND year <= 9999),
    topic            TEXT          NOT NULL,
    highlight        TEXT,
    body             TEXT          NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_management_writeups_owner_year
    ON management_writeups (owner_user_id, year);
CREATE INDEX IF NOT EXISTS idx_management_writeups_owner_year_updated
    ON management_writeups (owner_user_id, year, updated_at DESC);
