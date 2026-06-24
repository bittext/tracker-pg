-- Per-user Robinhood account tracker baselines (individual NBIS ledger + agentic vs SPX).

CREATE TABLE robinhood_account_tracker_config (
    id                          BIGSERIAL PRIMARY KEY,
    owner_user_id               BIGINT         NOT NULL,
    tracking_started_at         TIMESTAMPTZ    NOT NULL,
    individual_account_suffix   VARCHAR(8)     NOT NULL DEFAULT '3370',
    individual_baseline_nbis    NUMERIC(19, 6) NOT NULL,
    agentic_account_suffix      VARCHAR(8)     NOT NULL DEFAULT '3550',
    agentic_baseline_market_value NUMERIC(19, 2),
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_robinhood_account_tracker_config_owner UNIQUE (owner_user_id)
);

CREATE INDEX idx_robinhood_account_tracker_config_owner
    ON robinhood_account_tracker_config (owner_user_id);
