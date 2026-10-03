-- Life → Management → Auto Payments: recurring bank/card debits the owner enrolled.

CREATE TABLE management_auto_payments (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT         NOT NULL REFERENCES auth_users (id),
    desk                VARCHAR(16)    NOT NULL DEFAULT 'LIFE',
    name                TEXT           NOT NULL,
    payee               TEXT           NOT NULL DEFAULT '',
    category            TEXT           NOT NULL DEFAULT '',
    payment_method      VARCHAR(16)    NOT NULL DEFAULT 'ACH',
    frequency           VARCHAR(16)    NOT NULL DEFAULT 'MONTHLY',
    amount              NUMERIC(19, 4),
    currency            VARCHAR(8)     NOT NULL DEFAULT 'USD',
    started_on          DATE,
    next_payment_on     DATE,
    day_of_month        INTEGER,
    ended_on            DATE,
    status              VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    funding_account     TEXT           NOT NULL DEFAULT '',
    confirmation_ref    TEXT           NOT NULL DEFAULT '',
    website             TEXT           NOT NULL DEFAULT '',
    notes               TEXT           NOT NULL DEFAULT '',
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_management_auto_payments_day
        CHECK (day_of_month IS NULL OR (day_of_month >= 1 AND day_of_month <= 31))
);

CREATE INDEX idx_management_auto_payments_owner_desk
    ON management_auto_payments (owner_user_id, desk, status, name);
