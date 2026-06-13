-- Finance → Insurance: per-user policy tracking.

CREATE TABLE finance_insurance_policies (
    id                      BIGSERIAL PRIMARY KEY,
    owner_user_id           BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    carrier                 VARCHAR(255) NOT NULL,
    policy_type             VARCHAR(64)  NOT NULL,
    type_other              VARCHAR(255),
    policy_number           VARCHAR(128),
    coverage_description    VARCHAR(255) NOT NULL,
    premium_amount          NUMERIC(19, 2),
    premium_frequency       VARCHAR(32)  NOT NULL DEFAULT 'ANNUAL',
    coverage_start_date     DATE,
    coverage_end_date       DATE,
    renewal_reminder_days   INT          NOT NULL DEFAULT 30,
    notes                   TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_insurance_policies_owner ON finance_insurance_policies (owner_user_id);
CREATE INDEX idx_finance_insurance_policies_owner_end ON finance_insurance_policies (owner_user_id, coverage_end_date);
