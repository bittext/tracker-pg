-- Finance → Loans: per-user loan tracking.

CREATE TABLE finance_loans (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    institution         VARCHAR(255) NOT NULL,
    loan_nature         VARCHAR(64)  NOT NULL,
    nature_other        VARCHAR(255),
    date_availed        DATE,
    date_to_commence    DATE,
    current_balance     NUMERIC(19, 2),
    interest_rate       NUMERIC(8, 4),
    paid_so_far         NUMERIC(19, 2),
    balance_to_pay      NUMERIC(19, 2),
    payment_frequency   VARCHAR(32)  NOT NULL DEFAULT 'MONTHLY',
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_loans_owner ON finance_loans (owner_user_id);
CREATE INDEX idx_finance_loans_owner_nature ON finance_loans (owner_user_id, loan_nature);
