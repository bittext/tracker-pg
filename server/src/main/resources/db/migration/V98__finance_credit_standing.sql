-- Per-user credit standing / report snapshot (manual entry from AnnualCreditReport.com etc.).

CREATE TABLE finance_credit_standing (
    id                          BIGSERIAL PRIMARY KEY,
    owner_user_id               BIGINT       NOT NULL UNIQUE REFERENCES auth_users (id) ON DELETE CASCADE,
    score                       INTEGER,
    bureau                      VARCHAR(64),
    reported_as_of              DATE,
    notes                       TEXT,
    annual_report_pulled_at     DATE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_credit_standing_owner ON finance_credit_standing (owner_user_id);
