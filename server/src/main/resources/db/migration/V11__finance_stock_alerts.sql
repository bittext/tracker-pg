CREATE TABLE finance_notification_settings (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL REFERENCES auth_users (id),
    email_address       VARCHAR(320),
    mobile_e164         VARCHAR(32),
    email_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    sms_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_notification_settings_owner UNIQUE (owner_user_id)
);

CREATE TABLE finance_stock_alerts (
    id                                  BIGSERIAL PRIMARY KEY,
    owner_user_id                       BIGINT        NOT NULL REFERENCES auth_users (id),
    symbol                              VARCHAR(32)   NOT NULL,
    trigger_type                        VARCHAR(48)   NOT NULL,
    threshold_value                     NUMERIC(18,6) NOT NULL,
    repeat_mode                         VARCHAR(16)   NOT NULL,
    cooldown_minutes                    INTEGER       NOT NULL DEFAULT 1440,
    enabled                             BOOLEAN       NOT NULL DEFAULT TRUE,
    last_checked_at                     TIMESTAMPTZ,
    last_triggered_at                   TIMESTAMPTZ,
    last_regular_market_price           NUMERIC(18,6),
    last_regular_market_change_percent  NUMERIC(12,6),
    fire_count                          INTEGER       NOT NULL DEFAULT 0,
    created_at                          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_finance_stock_alert_trigger_type CHECK (
        trigger_type IN ('PRICE_AT_OR_ABOVE', 'SESSION_CHANGE_PERCENT_AT_OR_ABOVE')
    ),
    CONSTRAINT ck_finance_stock_alert_repeat_mode CHECK (repeat_mode IN ('ONCE', 'REPEAT')),
    CONSTRAINT ck_finance_stock_alert_cooldown CHECK (cooldown_minutes >= 0)
);

CREATE TABLE finance_alert_events (
    id                                  BIGSERIAL PRIMARY KEY,
    alert_id                            BIGINT REFERENCES finance_stock_alerts (id) ON DELETE SET NULL,
    owner_user_id                       BIGINT       NOT NULL REFERENCES auth_users (id),
    symbol                              VARCHAR(32),
    trigger_type                        VARCHAR(48),
    threshold_value                     NUMERIC(18,6),
    observed_price                      NUMERIC(18,6),
    observed_change_percent             NUMERIC(12,6),
    channel                             VARCHAR(16)  NOT NULL,
    status                              VARCHAR(24)  NOT NULL,
    message                             TEXT,
    provider_response                   TEXT,
    created_at                          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_finance_alert_event_channel CHECK (channel IN ('EMAIL', 'SMS', 'SYSTEM')),
    CONSTRAINT ck_finance_alert_event_status CHECK (status IN ('SENT', 'SKIPPED', 'FAILED'))
);

CREATE INDEX idx_finance_stock_alerts_enabled ON finance_stock_alerts (enabled, symbol);
CREATE INDEX idx_finance_stock_alerts_owner ON finance_stock_alerts (owner_user_id, symbol);
CREATE INDEX idx_finance_alert_events_owner_created ON finance_alert_events (owner_user_id, created_at DESC);
CREATE INDEX idx_finance_alert_events_alert_created ON finance_alert_events (alert_id, created_at DESC);
