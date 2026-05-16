-- Per-user labels for Management → Now roadmap card types (slug matches card.type in app data / localStorage).
CREATE TABLE management_now_card_types (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    slug VARCHAR(64) NOT NULL,
    label VARCHAR(120) NOT NULL,
    badge VARCHAR(32) NOT NULL,
    color_hex VARCHAR(16) NOT NULL,
    sort_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_management_now_card_types_owner_slug UNIQUE (owner_user_id, slug)
);

CREATE INDEX idx_management_now_card_types_owner ON management_now_card_types (owner_user_id);
