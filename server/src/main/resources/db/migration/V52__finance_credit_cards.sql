-- Finance → Credit: per-user credit cards and statement history.

CREATE TABLE finance_credit_cards (
    id                      BIGSERIAL PRIMARY KEY,
    owner_user_id           BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    institution             VARCHAR(255) NOT NULL,
    card_name               VARCHAR(255) NOT NULL,
    last_four               VARCHAR(4),
    credit_limit            NUMERIC(19, 2),
    current_balance         NUMERIC(19, 2),
    apr                     NUMERIC(8, 4),
    statement_balance       NUMERIC(19, 2),
    statement_date          DATE,
    payment_due_date        DATE,
    banking_institution_id  BIGINT       REFERENCES banking_institutions (id) ON DELETE SET NULL,
    notes                   TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_credit_cards_owner ON finance_credit_cards (owner_user_id);
CREATE INDEX idx_finance_credit_cards_owner_institution ON finance_credit_cards (owner_user_id, institution);

CREATE TABLE finance_credit_card_statements (
    id                  BIGSERIAL PRIMARY KEY,
    credit_card_id      BIGINT       NOT NULL REFERENCES finance_credit_cards (id) ON DELETE CASCADE,
    owner_user_id       BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    statement_date      DATE         NOT NULL,
    statement_balance   NUMERIC(19, 2),
    minimum_payment     NUMERIC(19, 2),
    payment_due_date    DATE,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_finance_credit_card_statements_card ON finance_credit_card_statements (credit_card_id);
CREATE INDEX idx_finance_credit_card_statements_owner ON finance_credit_card_statements (owner_user_id);
