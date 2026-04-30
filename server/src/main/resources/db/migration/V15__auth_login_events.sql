-- Per-sign-in audit trail (success, failure, MFA steps, logout). Not for security log shipping — query via Admin API.

CREATE TABLE auth_login_events (
    id                 BIGSERIAL PRIMARY KEY,
    event_type         VARCHAR(32)   NOT NULL,
    user_id            BIGINT        REFERENCES auth_users (id) ON DELETE SET NULL,
    username_shown     VARCHAR(120)  NOT NULL,
    client_ip          VARCHAR(64)   NOT NULL,
    user_agent         TEXT,
    detail             VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_login_events_created_at ON auth_login_events (created_at DESC);
CREATE INDEX idx_auth_login_events_user_id ON auth_login_events (user_id);
