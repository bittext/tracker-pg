-- Day-scoped work log entries (multiple per calendar day per user). Markdown body; no attachments in v1.

CREATE TABLE management_work_log_entries (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    entry_date      DATE          NOT NULL,
    logged_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    subject         VARCHAR(500)  NOT NULL DEFAULT '',
    body            TEXT          NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_management_work_log_owner_entry_date ON management_work_log_entries (owner_user_id, entry_date DESC);
CREATE INDEX idx_management_work_log_owner_logged_at ON management_work_log_entries (owner_user_id, logged_at DESC);
