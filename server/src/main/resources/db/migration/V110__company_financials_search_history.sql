-- Recent company lookups for Markets -> Research -> Financials, per owner. One row per
-- (owner, symbol); re-searching bumps searched_at rather than duplicating.

CREATE TABLE finance_company_financials_search (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    symbol TEXT NOT NULL,
    company_name TEXT,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_fin_co_fin_search_owner_symbol
    ON finance_company_financials_search (owner_user_id, symbol);

CREATE INDEX idx_fin_co_fin_search_owner_time
    ON finance_company_financials_search (owner_user_id, searched_at DESC);
