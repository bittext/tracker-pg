-- Per-user report calendar: dated notes by calendar type (birthday, work, personal) for the Reports screen.

CREATE TABLE report_calendar_entries (
    id                BIGSERIAL PRIMARY KEY,
    owner_user_id     BIGINT         NOT NULL REFERENCES auth_users (id),
    entry_date        DATE           NOT NULL,
    calendar_type     VARCHAR(32)    NOT NULL,
    title             VARCHAR(200),
    body              TEXT,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_report_cal_owner_type_date ON report_calendar_entries (owner_user_id, calendar_type, entry_date);
