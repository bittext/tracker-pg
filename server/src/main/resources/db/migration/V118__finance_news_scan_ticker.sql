-- Per-user Markets header ticker list for today’s worldwide news scan.

CREATE TABLE finance_news_scan_ticker (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    symbol          VARCHAR(16)  NOT NULL,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_news_scan_ticker_owner_symbol UNIQUE (owner_user_id, symbol)
);

CREATE INDEX idx_finance_news_scan_ticker_owner_sort
    ON finance_news_scan_ticker (owner_user_id, sort_order, id);
