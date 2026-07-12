-- Markets (trading) entitlement + audit log for v11 Life vs Markets split.

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS markets_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve access for existing accounts that already use Robinhood/trading.
UPDATE auth_users SET markets_enabled = TRUE WHERE markets_enabled = FALSE;

CREATE TABLE IF NOT EXISTS markets_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES auth_users (id),
    username        VARCHAR(120) NOT NULL,
    action          VARCHAR(120) NOT NULL,
    http_method     VARCHAR(16) NOT NULL,
    request_path    VARCHAR(512) NOT NULL,
    client_ip       VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_markets_audit_log_user_created
    ON markets_audit_log (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_markets_audit_log_created
    ON markets_audit_log (created_at DESC);
