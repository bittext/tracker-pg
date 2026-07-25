-- Cached AI speculative outlook for Then & now (one latest row per owner).
CREATE TABLE finance_investment_then_now_outlook (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    horizon_months  INT          NOT NULL DEFAULT 6,
    model           VARCHAR(128) NOT NULL DEFAULT '',
    outlook_json    TEXT         NOT NULL,
    scenario_ids    VARCHAR(512) NOT NULL DEFAULT '',
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_investment_then_now_outlook_owner UNIQUE (owner_user_id)
);

CREATE INDEX idx_finance_itn_outlook_owner_generated
    ON finance_investment_then_now_outlook (owner_user_id, generated_at DESC);
