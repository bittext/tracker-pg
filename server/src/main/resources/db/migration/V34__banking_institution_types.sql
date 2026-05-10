-- Per-owner institution types (e.g. Checking, Brokerage, Credit card) for banking imports and typed reports.

CREATE TABLE banking_institution_types (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    name            TEXT          NOT NULL,
    sort_order      INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_banking_institution_type_owner_name UNIQUE (owner_user_id, name)
);

CREATE INDEX idx_banking_institution_types_owner ON banking_institution_types (owner_user_id);

ALTER TABLE banking_institutions
    ADD COLUMN institution_type_id BIGINT REFERENCES banking_institution_types (id) ON DELETE SET NULL;

CREATE INDEX idx_banking_institutions_institution_type ON banking_institutions (institution_type_id);
