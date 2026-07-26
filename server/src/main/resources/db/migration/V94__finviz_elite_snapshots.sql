-- Cached Finviz Elite CSV snapshots (system-wide; keyed by cache_key).
CREATE TABLE IF NOT EXISTS finance_finviz_elite_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    cache_key       VARCHAR(512) NOT NULL,
    source_label    VARCHAR(256),
    columns_json    TEXT NOT NULL,
    rows_json       TEXT NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_finance_finviz_elite_snapshot_key UNIQUE (cache_key)
);

CREATE INDEX IF NOT EXISTS idx_finance_finviz_elite_snapshot_expires
    ON finance_finviz_elite_snapshot (expires_at);
