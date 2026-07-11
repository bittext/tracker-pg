-- Cached LLM insights for Robinhood Daily Tracker (per owner + period).

CREATE TABLE rh_daily_tracker_ai_insight (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    scope           VARCHAR(16)  NOT NULL,
    period_key      VARCHAR(32)  NOT NULL,
    facts_hash      VARCHAR(64)  NOT NULL,
    insight_json    TEXT         NOT NULL,
    model           VARCHAR(128) NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_daily_tracker_ai_insight_owner_scope_period
        UNIQUE (owner_user_id, scope, period_key),
    CONSTRAINT ck_rh_daily_tracker_ai_insight_scope
        CHECK (scope IN ('YEAR', 'MONTH', 'WEEK', 'DAY'))
);

CREATE INDEX idx_rh_daily_tracker_ai_insight_owner
    ON rh_daily_tracker_ai_insight (owner_user_id);
