-- Life → Management → Subscriptions: paid plans the owner has enrolled in.

CREATE TABLE management_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id),
    desk            VARCHAR(16)    NOT NULL DEFAULT 'LIFE',
    name            TEXT           NOT NULL,
    vendor          TEXT           NOT NULL DEFAULT '',
    category        TEXT           NOT NULL DEFAULT '',
    plan            TEXT           NOT NULL DEFAULT '',
    billing_cycle   VARCHAR(16)    NOT NULL DEFAULT 'ANNUAL',
    amount          NUMERIC(19, 4),
    currency        VARCHAR(8)     NOT NULL DEFAULT 'USD',
    enrolled_on     DATE,
    renews_on       DATE,
    trial_ends_on   DATE,
    cancelled_on    DATE,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    auto_renew      BOOLEAN        NOT NULL DEFAULT TRUE,
    website         TEXT           NOT NULL DEFAULT '',
    account_email   TEXT           NOT NULL DEFAULT '',
    notes           TEXT           NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_management_subscriptions_owner_desk
    ON management_subscriptions (owner_user_id, desk, status, name);
