-- Markets Journey: target vs actual trajectory toward milestones (e.g. first million).

CREATE TABLE markets_journeys (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    title               VARCHAR(200)   NOT NULL,
    milestone_amount    NUMERIC(18, 2) NOT NULL,
    sort_order          INT            NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_markets_journeys_owner_title UNIQUE (owner_user_id, title)
);

CREATE INDEX idx_markets_journeys_owner ON markets_journeys (owner_user_id);

CREATE TABLE markets_journey_entries (
    id                  BIGSERIAL PRIMARY KEY,
    journey_id          BIGINT         NOT NULL REFERENCES markets_journeys (id) ON DELETE CASCADE,
    owner_user_id       BIGINT         NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    period_date         DATE           NOT NULL,
    period_label        VARCHAR(64)    NOT NULL DEFAULT '',
    target_amount       NUMERIC(18, 2),
    actual_amount       NUMERIC(18, 2),
    target_note         TEXT,
    actual_note         TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_markets_journey_entries_period UNIQUE (journey_id, period_date)
);

CREATE INDEX idx_markets_journey_entries_journey ON markets_journey_entries (journey_id, period_date);
CREATE INDEX idx_markets_journey_entries_owner ON markets_journey_entries (owner_user_id);
