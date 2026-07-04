-- Admin-managed scheduled jobs (Admin → Management → Cron Jobs).

CREATE TABLE admin_cron_job (
    job_key            VARCHAR(64)  PRIMARY KEY,
    display_name       VARCHAR(128) NOT NULL,
    description        TEXT,
    category           VARCHAR(32)  NOT NULL,
    schedule_type      VARCHAR(16)  NOT NULL,
    cron_expression    VARCHAR(128),
    fixed_delay_ms     BIGINT,
    initial_delay_ms   BIGINT       NOT NULL DEFAULT 0,
    zone_id            VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    built_in           BOOLEAN      NOT NULL DEFAULT TRUE,
    runner_key         VARCHAR(64)  NOT NULL,
    last_run_at        TIMESTAMPTZ,
    last_run_status    VARCHAR(16),
    last_run_message   TEXT,
    next_run_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_admin_cron_job_schedule_type CHECK (schedule_type IN ('CRON', 'FIXED_DELAY')),
    CONSTRAINT chk_admin_cron_job_schedule_shape CHECK (
        (schedule_type = 'CRON' AND cron_expression IS NOT NULL AND TRIM(cron_expression) <> '')
        OR (schedule_type = 'FIXED_DELAY' AND fixed_delay_ms IS NOT NULL AND fixed_delay_ms > 0)
    )
);

CREATE INDEX idx_admin_cron_job_enabled ON admin_cron_job (enabled);
CREATE INDEX idx_admin_cron_job_next_run ON admin_cron_job (next_run_at);
