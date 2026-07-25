-- Saved "then vs now" investment replays (user-scoped reference answers).
CREATE TABLE finance_investment_then_now (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    symbol               VARCHAR(32)    NOT NULL,
    company_name         VARCHAR(256)   NOT NULL DEFAULT '',
    invested_amount      NUMERIC(18, 2) NOT NULL,
    as_of_date           DATE           NOT NULL,
    price_as_of_date     NUMERIC(18, 6) NOT NULL,
    price_as_of_session  DATE           NOT NULL,
    shares               NUMERIC(24, 8) NOT NULL,
    price_now            NUMERIC(18, 6) NOT NULL,
    price_now_session    DATE           NOT NULL,
    worth_now            NUMERIC(18, 2) NOT NULL,
    gain_amount          NUMERIC(18, 2) NOT NULL,
    gain_percent         NUMERIC(12, 4) NOT NULL,
    detail_answer        TEXT           NOT NULL,
    price_source         VARCHAR(64)    NOT NULL DEFAULT 'yahoo-chart',
    computed_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_investment_then_now_owner_key
        UNIQUE (owner_user_id, symbol, as_of_date, invested_amount)
);

CREATE INDEX idx_finance_investment_then_now_owner_updated
    ON finance_investment_then_now (owner_user_id, updated_at DESC);

CREATE INDEX idx_finance_investment_then_now_owner_symbol
    ON finance_investment_then_now (owner_user_id, symbol);
