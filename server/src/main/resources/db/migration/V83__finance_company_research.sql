-- Company research / earnings watch: per-user cards, decision status, and searchable notes.

CREATE TABLE finance_company_research (
    id                   BIGSERIAL PRIMARY KEY,
    owner_user_id        BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    symbol               VARCHAR(32)   NOT NULL,
    company_name         VARCHAR(256)  NOT NULL DEFAULT '',
    decision_status      VARCHAR(24)   NOT NULL DEFAULT 'WATCHING',
    tags                 VARCHAR(512)  NOT NULL DEFAULT '',
    thesis               TEXT          NOT NULL DEFAULT '',
    next_earnings_date   DATE,
    next_earnings_timing VARCHAR(32),
    last_viewed_at       TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_company_research_owner_symbol UNIQUE (owner_user_id, symbol),
    CONSTRAINT ck_finance_company_research_status CHECK (
        decision_status IN ('WATCHING', 'CONSIDERING', 'BOUGHT', 'PASSED', 'REVISIT')
    )
);

CREATE INDEX idx_finance_company_research_owner_status
    ON finance_company_research (owner_user_id, decision_status, symbol);

CREATE INDEX idx_finance_company_research_owner_earnings
    ON finance_company_research (owner_user_id, next_earnings_date);

CREATE INDEX idx_finance_company_research_owner_updated
    ON finance_company_research (owner_user_id, updated_at DESC);

CREATE TABLE finance_company_research_note (
    id            BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    research_id   BIGINT       NOT NULL REFERENCES finance_company_research (id) ON DELETE CASCADE,
    note_text     TEXT         NOT NULL,
    tags          VARCHAR(512) NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_finance_company_research_note_text CHECK (char_length(trim(note_text)) > 0)
);

CREATE INDEX idx_finance_company_research_note_research
    ON finance_company_research_note (research_id, created_at DESC);

CREATE INDEX idx_finance_company_research_note_owner
    ON finance_company_research_note (owner_user_id, created_at DESC);
