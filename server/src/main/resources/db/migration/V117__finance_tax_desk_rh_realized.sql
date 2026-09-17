-- Calendar-year Robinhood get_realized_pnl totals used by Tax desk (not 1099-B).

CREATE TABLE finance_tax_desk_rh_realized (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    tax_year INT NOT NULL,
    as_of_date DATE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_realized NUMERIC(19, 2) NOT NULL,
    accounts_json TEXT NOT NULL DEFAULT '[]',
    warnings TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_tax_desk_rh_realized UNIQUE (owner_user_id, tax_year, as_of_date)
);

CREATE INDEX idx_finance_tax_desk_rh_realized_owner_year
    ON finance_tax_desk_rh_realized (owner_user_id, tax_year, as_of_date DESC);
