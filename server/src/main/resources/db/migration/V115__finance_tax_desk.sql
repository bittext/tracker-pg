-- Daily estimated-tax working papers (Trades → Tax desk).
-- W-2 / external income, estimated payments, and one saved workbook per calendar day.

CREATE TABLE finance_tax_desk_settings (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    tax_year INT NOT NULL,
    filing_status TEXT NOT NULL DEFAULT 'MARRIED_FILING_JOINTLY',
    resident_state TEXT NOT NULL DEFAULT 'TX',
    short_term_loss_carryover NUMERIC(19, 2) NOT NULL DEFAULT 0,
    long_term_loss_carryover NUMERIC(19, 2) NOT NULL DEFAULT 0,
    prior_year_agi NUMERIC(19, 2) NOT NULL DEFAULT 0,
    prior_year_tax NUMERIC(19, 2) NOT NULL DEFAULT 0,
    child_tax_credit NUMERIC(19, 2) NOT NULL DEFAULT 0,
    target_refund NUMERIC(19, 2) NOT NULL DEFAULT 5000,
    notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_tax_desk_settings UNIQUE (owner_user_id, tax_year)
);

CREATE TABLE finance_tax_desk_income_items (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    tax_year INT NOT NULL,
    kind TEXT NOT NULL,
    payer TEXT NOT NULL DEFAULT '',
    ytd_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    annual_projected NUMERIC(19, 2) NOT NULL DEFAULT 0,
    withholding_ytd NUMERIC(19, 2) NOT NULL DEFAULT 0,
    withholding_annual_projected NUMERIC(19, 2) NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_finance_tax_desk_income_owner_year
    ON finance_tax_desk_income_items (owner_user_id, tax_year, sort_order, id);

CREATE TABLE finance_tax_desk_payments (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    tax_year INT NOT NULL,
    paid_on DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    method TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'MANUAL',
    source_ref TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_finance_tax_desk_payments_owner_year
    ON finance_tax_desk_payments (owner_user_id, tax_year, paid_on, id);

CREATE TABLE finance_tax_desk_daily_snapshots (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    tax_year INT NOT NULL,
    as_of_date DATE NOT NULL,
    risk_level TEXT NOT NULL,
    estimated_tax NUMERIC(19, 2) NOT NULL,
    filing_balance NUMERIC(19, 2) NOT NULL,
    penalty_exposure NUMERIC(19, 2) NOT NULL,
    realized_ytd NUMERIC(19, 2) NOT NULL,
    workbook_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_tax_desk_daily UNIQUE (owner_user_id, tax_year, as_of_date)
);

CREATE INDEX idx_finance_tax_desk_daily_owner_year
    ON finance_tax_desk_daily_snapshots (owner_user_id, tax_year, as_of_date DESC);
