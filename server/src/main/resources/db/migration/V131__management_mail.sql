-- Life → Management → Mail: mailbox credentials and the three daily digests.

CREATE TABLE management_mail_account (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    email_address VARCHAR(320) NOT NULL,
    secret_enc TEXT NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_management_mail_account UNIQUE (owner_user_id, provider, email_address)
);

CREATE INDEX ix_management_mail_account_owner ON management_mail_account (owner_user_id);

CREATE TABLE management_mail_digest (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    digest_on DATE NOT NULL,
    slot VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    summary TEXT NOT NULL DEFAULT '',
    message_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'OK',
    CONSTRAINT ux_management_mail_digest UNIQUE (owner_user_id, digest_on, slot)
);

CREATE INDEX ix_management_mail_digest_owner ON management_mail_digest (owner_user_id, digest_on DESC);

CREATE TABLE management_mail_digest_message (
    id BIGSERIAL PRIMARY KEY,
    digest_id BIGINT NOT NULL REFERENCES management_mail_digest (id) ON DELETE CASCADE,
    provider VARCHAR(16) NOT NULL,
    mailbox VARCHAR(320) NOT NULL,
    from_line VARCHAR(500) NOT NULL DEFAULT '',
    subject VARCHAR(500) NOT NULL DEFAULT '',
    received_at TIMESTAMPTZ,
    snippet TEXT NOT NULL DEFAULT ''
);

CREATE INDEX ix_management_mail_digest_message_digest ON management_mail_digest_message (digest_id);
