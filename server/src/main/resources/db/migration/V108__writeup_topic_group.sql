-- Explicit topic groups for Management write-ups. Seed from the old prefix-stem rule
-- so existing families (e.g. NBIS / NBIS options) stay grouped after deploy.

ALTER TABLE management_writeups
    ADD COLUMN IF NOT EXISTS topic_group TEXT,
    ADD COLUMN IF NOT EXISTS topic_group_sort INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_management_writeups_owner_year_group
    ON management_writeups (owner_user_id, year, topic_group);

WITH norm AS (
    SELECT
        id,
        owner_user_id,
        year,
        topic,
        updated_at,
        regexp_replace(
            regexp_replace(trim(lower(coalesce(topic, ''))), '\s+', ' ', 'g'),
            '[\s.,;:!?]+$',
            ''
        ) AS n
    FROM management_writeups
    WHERE topic_group IS NULL
),
stems AS (
    SELECT DISTINCT owner_user_id, year, n
    FROM norm
    WHERE length(n) >= 2
),
keyed AS (
    SELECT
        n.id,
        n.owner_user_id,
        n.year,
        n.topic,
        n.updated_at,
        n.n,
        coalesce(
            (
                SELECT s.n
                FROM stems s
                WHERE s.owner_user_id = n.owner_user_id
                  AND s.year = n.year
                  AND length(s.n) >= 2
                  AND (n.n = s.n OR n.n LIKE s.n || ' %')
                ORDER BY length(s.n) ASC, s.n ASC
                LIMIT 1
            ),
            CASE WHEN n.n = '' THEN 'untitled' ELSE n.n END
        ) AS grp_key
    FROM norm n
),
labeled AS (
    SELECT
        k.*,
        count(*) OVER (PARTITION BY k.owner_user_id, k.year, k.grp_key) AS cnt,
        first_value(trim(k.topic)) OVER (
            PARTITION BY k.owner_user_id, k.year, k.grp_key
            ORDER BY length(trim(k.topic)) ASC, trim(k.topic) ASC
        ) AS grp_label,
        (row_number() OVER (
            PARTITION BY k.owner_user_id, k.year, k.grp_key
            ORDER BY k.updated_at DESC, k.id DESC
        ) - 1) AS grp_sort
    FROM keyed k
)
UPDATE management_writeups w
SET
    topic_group = nullif(trim(l.grp_label), ''),
    topic_group_sort = l.grp_sort
FROM labeled l
WHERE w.id = l.id
  AND l.cnt > 1;
