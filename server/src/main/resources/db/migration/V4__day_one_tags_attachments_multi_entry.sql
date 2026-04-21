-- Multiple journal lines per day; optional location/weather; shared tag catalog; file attachments.

ALTER TABLE management_day_one_logs
    ADD COLUMN IF NOT EXISTS location_text VARCHAR(512),
    ADD COLUMN IF NOT EXISTS weather_text VARCHAR(512);

DROP INDEX IF EXISTS uq_management_day_one_owner_day;

CREATE INDEX IF NOT EXISTS idx_management_day_one_owner_day ON management_day_one_logs (owner_user_id, logged_on);

CREATE TABLE management_day_one_tag_defs (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(120) NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE management_day_one_log_tags (
    log_id      BIGINT NOT NULL REFERENCES management_day_one_logs (id) ON DELETE CASCADE,
    tag_def_id  BIGINT NOT NULL REFERENCES management_day_one_tag_defs (id) ON DELETE CASCADE,
    PRIMARY KEY (log_id, tag_def_id)
);

CREATE INDEX idx_day_one_log_tags_tag ON management_day_one_log_tags (tag_def_id);

CREATE TABLE management_day_one_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    log_id             BIGINT NOT NULL REFERENCES management_day_one_logs (id) ON DELETE CASCADE,
    storage_key        VARCHAR(512) NOT NULL,
    original_filename  VARCHAR(512) NOT NULL,
    content_type       VARCHAR(255),
    size_bytes         BIGINT,
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_day_one_attachments_log ON management_day_one_attachments (log_id);
