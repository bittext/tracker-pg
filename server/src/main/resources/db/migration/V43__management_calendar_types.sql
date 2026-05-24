-- Per-user calendar type labels/codes for Management → Calendar (Admin → Management → Calendar).
CREATE TABLE management_calendar_types (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(120) NOT NULL,
    sort_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_management_calendar_types_owner_code UNIQUE (owner_user_id, code)
);

CREATE INDEX idx_management_calendar_types_owner ON management_calendar_types (owner_user_id);

-- Prefill existing users with the built-in calendar types.
INSERT INTO management_calendar_types (owner_user_id, code, label, sort_index, created_at)
SELECT u.id, v.code, v.label, v.sort_index, NOW()
FROM auth_users u
CROSS JOIN (
    VALUES
        ('BIRTHDAY', 'Birthday', 0),
        ('WORK', 'Work', 1),
        ('PERSONAL', 'Personal', 2),
        ('TRADES', 'Trades', 3),
        ('BANKING', 'Banking', 4),
        ('PAYMENTS', 'Payments', 5),
        ('OPINION_STRATEGIES', 'Opinion & strategies', 6)
) AS v(code, label, sort_index)
ON CONFLICT DO NOTHING;
