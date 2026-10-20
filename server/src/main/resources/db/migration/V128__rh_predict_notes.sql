-- Notes a person writes about their Predict book, plus the latest AI feedback on those notes.

CREATE TABLE rh_predict_note (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    body            TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rh_predict_note_owner_created
    ON rh_predict_note (owner_user_id, created_at DESC);

CREATE TABLE rh_predict_note_feedback (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    headline        TEXT          NOT NULL DEFAULT '',
    points_json     TEXT          NOT NULL DEFAULT '[]',
    watch           TEXT          NOT NULL DEFAULT '',
    model           VARCHAR(128)  NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rh_predict_note_feedback_owner UNIQUE (owner_user_id)
);
