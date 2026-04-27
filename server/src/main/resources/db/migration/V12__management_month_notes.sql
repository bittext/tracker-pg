-- Month-scoped management notes (no day granularity) with optional file attachments.

CREATE TABLE management_month_notes (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    year            INT           NOT NULL CHECK (year >= 1970 AND year <= 9999),
    month           INT           NOT NULL CHECK (month >= 1 AND month <= 12),
    subject         TEXT          NOT NULL,
    body            TEXT          NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_management_month_notes_owner_ym ON management_month_notes (owner_user_id, year, month);
CREATE INDEX idx_management_month_notes_owner ON management_month_notes (owner_user_id);

CREATE TABLE management_month_note_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    note_id            BIGINT        NOT NULL REFERENCES management_month_notes (id) ON DELETE CASCADE,
    storage_key        TEXT          NOT NULL,
    original_filename  TEXT          NOT NULL,
    content_type       TEXT,
    size_bytes         BIGINT        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_management_month_note_att_note ON management_month_note_attachments (note_id);
