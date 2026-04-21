-- Per-user journal: Markdown entries, tag catalog, many-to-many tags, file attachments.

CREATE TABLE journal_tag_defs (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id),
    name            VARCHAR(120)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_journal_tag_owner_name UNIQUE (owner_user_id, name)
);

CREATE INDEX idx_journal_tag_defs_owner ON journal_tag_defs (owner_user_id);

CREATE TABLE journal_entries (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT         NOT NULL REFERENCES auth_users (id),
    logged_on       DATE           NOT NULL,
    body_markdown   TEXT           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_journal_entries_owner_day ON journal_entries (owner_user_id, logged_on DESC);

CREATE TABLE journal_entry_tags (
    entry_id        BIGINT NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    tag_id          BIGINT NOT NULL REFERENCES journal_tag_defs (id) ON DELETE CASCADE,
    PRIMARY KEY (entry_id, tag_id)
);

CREATE INDEX idx_journal_entry_tags_tag ON journal_entry_tags (tag_id);

CREATE TABLE journal_attachments (
    id                 BIGSERIAL PRIMARY KEY,
    entry_id           BIGINT        NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    storage_key        VARCHAR(512)  NOT NULL,
    original_filename  VARCHAR(512)  NOT NULL,
    content_type       VARCHAR(255),
    size_bytes         BIGINT,
    created_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_journal_attachments_entry ON journal_attachments (entry_id);
