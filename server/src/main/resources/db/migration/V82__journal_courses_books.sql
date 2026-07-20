-- Journal → Courses and Books learning trackers (per user).

CREATE TABLE journal_courses (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    title           VARCHAR(512)  NOT NULL,
    provider        VARCHAR(256),
    status          VARCHAR(32)   NOT NULL DEFAULT 'IN_PROGRESS',
    url             TEXT,
    notes_markdown  TEXT          NOT NULL DEFAULT '',
    started_on      DATE,
    completed_on    DATE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_journal_course_status CHECK (status IN ('INTEND', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_journal_courses_owner_status ON journal_courses (owner_user_id, status, updated_at DESC);
CREATE INDEX idx_journal_courses_owner_updated ON journal_courses (owner_user_id, updated_at DESC);

CREATE TABLE journal_books (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT        NOT NULL REFERENCES auth_users (id),
    title           VARCHAR(512)  NOT NULL,
    author          VARCHAR(256),
    status          VARCHAR(32)   NOT NULL DEFAULT 'READING',
    url             TEXT,
    notes_markdown  TEXT          NOT NULL DEFAULT '',
    started_on      DATE,
    finished_on     DATE,
    rating          SMALLINT CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_journal_book_status CHECK (status IN ('TO_READ', 'READING', 'FINISHED'))
);

CREATE INDEX idx_journal_books_owner_status ON journal_books (owner_user_id, status, updated_at DESC);
CREATE INDEX idx_journal_books_owner_updated ON journal_books (owner_user_id, updated_at DESC);
