-- Finance → Investments: per-user holdings tracking.

CREATE TABLE finance_investments (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    institution         VARCHAR(255) NOT NULL,
    investment_type     VARCHAR(64)  NOT NULL,
    type_other          VARCHAR(255),
    symbol              VARCHAR(64),
    name                VARCHAR(255) NOT NULL,
    date_acquired       DATE,
    quantity            NUMERIC(19, 6),
    cost_basis          NUMERIC(19, 2),
    current_value       NUMERIC(19, 2),
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_investments_owner ON finance_investments (owner_user_id);
CREATE INDEX idx_finance_investments_owner_type ON finance_investments (owner_user_id, investment_type);
