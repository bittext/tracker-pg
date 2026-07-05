-- Per-account spike email alerts for Robinhood Daily Tracker captures.

CREATE TABLE rh_daily_tracker_account_alert (
    id                          BIGSERIAL PRIMARY KEY,
    owner_user_id               BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix              VARCHAR(8)     NOT NULL,
    enabled                     BOOLEAN        NOT NULL DEFAULT FALSE,
    value_dollars_enabled       BOOLEAN        NOT NULL DEFAULT FALSE,
    min_value_change_dollars    NUMERIC(19, 2),
    value_percent_enabled       BOOLEAN        NOT NULL DEFAULT FALSE,
    min_value_change_percent    NUMERIC(8, 4),
    position_change_enabled     BOOLEAN        NOT NULL DEFAULT FALSE,
    cooldown_minutes            INTEGER        NOT NULL DEFAULT 60,
    last_triggered_at           TIMESTAMPTZ,
    last_triggered_snapshot_id  BIGINT,
    created_at                  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_daily_tracker_account_alert_owner_suffix UNIQUE (owner_user_id, account_suffix),
    CONSTRAINT ck_rh_daily_tracker_alert_cooldown CHECK (cooldown_minutes >= 0)
);

CREATE INDEX idx_rh_daily_tracker_account_alert_owner
    ON rh_daily_tracker_account_alert (owner_user_id);

CREATE TABLE rh_daily_tracker_alert_event (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    account_suffix      VARCHAR(8)     NOT NULL,
    snapshot_id         BIGINT,
    prior_snapshot_id   BIGINT,
    trigger_reasons     VARCHAR(128)   NOT NULL,
    delta_dollars       NUMERIC(19, 2),
    delta_percent       NUMERIC(8, 4),
    email_status        VARCHAR(16)    NOT NULL,
    destination_masked  TEXT,
    detail              TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rh_daily_tracker_alert_event_owner_created
    ON rh_daily_tracker_alert_event (owner_user_id, created_at DESC);
