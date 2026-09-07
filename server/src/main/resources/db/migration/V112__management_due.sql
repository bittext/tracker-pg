-- Management → Due: payables and receivables on a monthly calendar.

CREATE TABLE management_due_items (
    id               BIGSERIAL PRIMARY KEY,
    owner_user_id    BIGINT         NOT NULL REFERENCES auth_users (id),
    side             TEXT           NOT NULL CHECK (side IN ('PAYABLE', 'RECEIVABLE')),
    counterparty     TEXT           NOT NULL,
    recurring        BOOLEAN        NOT NULL DEFAULT FALSE,
    day_of_month     INT            CHECK (day_of_month IS NULL OR (day_of_month >= 1 AND day_of_month <= 31)),
    one_off_date     DATE,
    starts_on        DATE           NOT NULL,
    amount_override  NUMERIC(19, 2),
    notes            TEXT           NOT NULL DEFAULT '',
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CHECK (
        (recurring = TRUE AND day_of_month IS NOT NULL AND one_off_date IS NULL)
        OR (recurring = FALSE AND one_off_date IS NOT NULL)
    )
);

CREATE INDEX idx_management_due_items_owner_active
    ON management_due_items (owner_user_id, active);

CREATE TABLE management_due_occurrences (
    id              BIGSERIAL PRIMARY KEY,
    item_id         BIGINT         NOT NULL REFERENCES management_due_items (id) ON DELETE CASCADE,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id),
    year            INT            NOT NULL CHECK (year >= 1970 AND year <= 9999),
    month           INT            NOT NULL CHECK (month >= 1 AND month <= 12),
    settled         BOOLEAN        NOT NULL DEFAULT FALSE,
    settled_amount  NUMERIC(19, 2),
    settled_at      TIMESTAMPTZ,
    UNIQUE (item_id, year, month)
);

CREATE INDEX idx_management_due_occ_owner_ym
    ON management_due_occurrences (owner_user_id, year, month);
