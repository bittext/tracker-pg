-- Daily journal entries ("Day One") scoped per user for Management + Reports.

CREATE TABLE management_day_one_logs (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    logged_on       DATE          NOT NULL,
    entry_text      TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX uq_management_day_one_owner_day ON management_day_one_logs (owner_user_id, logged_on);
CREATE INDEX idx_management_day_one_owner_logged ON management_day_one_logs (owner_user_id, logged_on DESC);
