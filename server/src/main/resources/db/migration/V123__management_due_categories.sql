-- Due subcategories, configured per account and shown highest sort order first.

CREATE TABLE management_due_categories (
    id             BIGSERIAL PRIMARY KEY,
    owner_user_id  BIGINT      NOT NULL REFERENCES auth_users (id),
    name           TEXT        NOT NULL,
    sort_order     INT         NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_management_due_categories_owner_name
    ON management_due_categories (owner_user_id, lower(name));

CREATE INDEX idx_management_due_categories_owner_sort
    ON management_due_categories (owner_user_id, sort_order DESC);

INSERT INTO management_due_categories (owner_user_id, name, sort_order, created_at)
SELECT u.id, v.name, v.sort_order, NOW()
FROM auth_users u
CROSS JOIN (
    VALUES
        ('Payrolls', 1000),
        ('Loans', 900),
        ('Utilities', 800),
        ('Miscellaneous', 700),
        ('Interests', 600),
        ('Transfers', 500),
        ('Credit Cards', 400),
        ('Insurance Premiums', 300),
        ('Learnings', 200),
        ('Medicals', 100)
) AS v(name, sort_order);

ALTER TABLE management_due_items
    ADD COLUMN category_id BIGINT REFERENCES management_due_categories (id) ON DELETE SET NULL;
