-- Trading Journal: day-centric reflection linked to Daily Tracker (9 PM CT wrap).

CREATE TABLE trading_journal_entry (
    id                      BIGSERIAL PRIMARY KEY,
    owner_user_id           BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    snapshot_date           DATE           NOT NULL,
    title                   VARCHAR(256)   NOT NULL DEFAULT '',
    body_markdown           TEXT           NOT NULL DEFAULT '',
    tags                    VARCHAR(512)   NOT NULL DEFAULT '',
    process_grade           SMALLINT,
    risk_grade              SMALLINT,
    linked_summary_note     BOOLEAN        NOT NULL DEFAULT FALSE,
    has_scheduled_close     BOOLEAN        NOT NULL DEFAULT FALSE,
    close_combined_total    NUMERIC(18, 2),
    close_combined_change   NUMERIC(18, 2),
    close_pulled_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_trading_journal_entry_owner_date UNIQUE (owner_user_id, snapshot_date),
    CONSTRAINT ck_trading_journal_process_grade CHECK (process_grade IS NULL OR (process_grade BETWEEN 1 AND 5)),
    CONSTRAINT ck_trading_journal_risk_grade CHECK (risk_grade IS NULL OR (risk_grade BETWEEN 1 AND 5))
);

CREATE INDEX idx_trading_journal_entry_owner_date
    ON trading_journal_entry (owner_user_id, snapshot_date DESC);

CREATE INDEX idx_trading_journal_entry_owner_updated
    ON trading_journal_entry (owner_user_id, updated_at DESC);

CREATE TABLE trading_journal_ref (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    entry_id        BIGINT        NOT NULL REFERENCES trading_journal_entry (id) ON DELETE CASCADE,
    kind            VARCHAR(24)   NOT NULL,
    symbol          VARCHAR(32),
    url             VARCHAR(1024),
    label           VARCHAR(256)  NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_trading_journal_ref_kind CHECK (kind IN ('SYMBOL', 'URL', 'NOTE'))
);

CREATE INDEX idx_trading_journal_ref_entry
    ON trading_journal_ref (entry_id, created_at DESC);

CREATE INDEX idx_trading_journal_ref_owner_symbol
    ON trading_journal_ref (owner_user_id, symbol)
    WHERE symbol IS NOT NULL;

CREATE TABLE trading_journal_attachment (
    id                 BIGSERIAL PRIMARY KEY,
    owner_user_id      BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    entry_id           BIGINT        NOT NULL REFERENCES trading_journal_entry (id) ON DELETE CASCADE,
    storage_key        VARCHAR(512)  NOT NULL,
    original_filename  VARCHAR(512)  NOT NULL,
    content_type       VARCHAR(255),
    size_bytes         BIGINT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trading_journal_attachment_entry
    ON trading_journal_attachment (entry_id, created_at DESC);
