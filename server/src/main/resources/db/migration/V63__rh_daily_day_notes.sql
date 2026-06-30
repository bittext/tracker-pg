-- Optional user notes for each Daily Tracker day (9 PM scheduled summary).

CREATE TABLE robinhood_rh_daily_day_note (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    snapshot_date   DATE          NOT NULL,
    note_text       TEXT          NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_daily_day_note_owner_date UNIQUE (owner_user_id, snapshot_date)
);

CREATE INDEX idx_rh_daily_day_note_owner_date
    ON robinhood_rh_daily_day_note (owner_user_id, snapshot_date DESC);
