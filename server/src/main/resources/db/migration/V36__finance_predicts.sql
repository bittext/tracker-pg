-- V36: Finance → Trading → Predicts community-sentiment analytics.
--
-- Stores explicitly tracked tickers per user, normalized community mentions
-- (StockTwits today, Reddit/X later), pre-aggregated bucket time series for
-- charting and spike detection, and per-(ticker, source, hour-of-week)
-- baselines recomputed nightly from the bucket history.
--
-- Indexes are tuned for the two hot paths:
--   (a) bucket time series for one ticker + source + window, last N days
--   (b) leaderboard / cross-ticker queries at a single bucket_size
-- ----------------------------------------------------------------------

-- Per-user tracked tickers. The seeded tickers (Robinhood holdings, watchlist
-- overlap) live alongside user-added ones; `auto_seeded` distinguishes them
-- so the quota check only considers manual additions.
CREATE TABLE finance_predicts_tickers (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    symbol VARCHAR(32) NOT NULL,
    auto_seeded BOOLEAN NOT NULL DEFAULT FALSE,
    sources_enabled VARCHAR(64) NOT NULL DEFAULT 'stocktwits',
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_predicts_tickers_owner_symbol UNIQUE (owner_user_id, symbol)
);

CREATE INDEX idx_finance_predicts_tickers_symbol ON finance_predicts_tickers (symbol);

-- Raw normalized mentions. (source, source_msg_id) is unique so the
-- StockTwits backfill into "since"-cursor mode never double-counts.
-- text_hash is the SHA-256 of the trimmed body and is used to de-duplicate
-- identical reposts/quotes when a source_msg_id is missing.
CREATE TABLE finance_predicts_mentions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    source_msg_id VARCHAR(128),
    text_hash CHAR(64),
    body TEXT NOT NULL,
    body_preview VARCHAR(240),
    author_hash CHAR(64),
    engagement_score INT NOT NULL DEFAULT 0,
    native_sentiment VARCHAR(16),
    sentiment_label VARCHAR(16),
    sentiment_score NUMERIC(6, 4),
    confidence NUMERIC(6, 4),
    posted_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    url VARCHAR(512)
);

CREATE UNIQUE INDEX uq_finance_predicts_mentions_src_msg
    ON finance_predicts_mentions (source, source_msg_id)
    WHERE source_msg_id IS NOT NULL;

CREATE INDEX idx_finance_predicts_mentions_symbol_posted
    ON finance_predicts_mentions (symbol, posted_at DESC);

CREATE INDEX idx_finance_predicts_mentions_source_posted
    ON finance_predicts_mentions (source, posted_at DESC);

-- Pre-aggregated buckets. bucket_size is the canonical width: 5m, 15m, 1h, 1d.
-- bucket_start is truncated to the bucket boundary (UTC). Upsert key is
-- (symbol, source, bucket_size, bucket_start).
CREATE TABLE finance_predicts_buckets (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    bucket_size VARCHAR(8) NOT NULL,
    bucket_start TIMESTAMPTZ NOT NULL,
    msg_count INT NOT NULL DEFAULT 0,
    unique_authors INT NOT NULL DEFAULT 0,
    pos_count INT NOT NULL DEFAULT 0,
    neg_count INT NOT NULL DEFAULT 0,
    neu_count INT NOT NULL DEFAULT 0,
    engagement_sum INT NOT NULL DEFAULT 0,
    sentiment_sum NUMERIC(12, 4) NOT NULL DEFAULT 0,
    sentiment_avg NUMERIC(6, 4),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_predicts_buckets UNIQUE (symbol, source, bucket_size, bucket_start)
);

CREATE INDEX idx_finance_predicts_buckets_size_start
    ON finance_predicts_buckets (bucket_size, bucket_start DESC);

CREATE INDEX idx_finance_predicts_buckets_symbol_size_start
    ON finance_predicts_buckets (symbol, bucket_size, bucket_start DESC);

-- Per-(ticker, source, bucket_size) rolling baselines indexed by hour_of_week
-- (0..167). Recomputed nightly from the last N days of buckets and used as
-- the denominator for spike z-scores.
CREATE TABLE finance_predicts_baselines (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    bucket_size VARCHAR(8) NOT NULL,
    hour_of_week SMALLINT NOT NULL CHECK (hour_of_week BETWEEN 0 AND 167),
    msg_count_mean NUMERIC(10, 4) NOT NULL DEFAULT 0,
    msg_count_stddev NUMERIC(10, 4) NOT NULL DEFAULT 0,
    unique_authors_mean NUMERIC(10, 4) NOT NULL DEFAULT 0,
    unique_authors_stddev NUMERIC(10, 4) NOT NULL DEFAULT 0,
    sample_size INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_finance_predicts_baselines UNIQUE (symbol, source, bucket_size, hour_of_week)
);

-- Source health: latest poll outcome per source (rolling). The Predicts UI
-- reads this for the source-strip cards (last fetch, mentions in last 24h,
-- error counts). 1 row per source string (`stocktwits`, `reddit`, `x`).
CREATE TABLE finance_predicts_source_health (
    source VARCHAR(32) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error_message VARCHAR(500),
    consecutive_failures INT NOT NULL DEFAULT 0,
    mentions_ingested_24h INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
