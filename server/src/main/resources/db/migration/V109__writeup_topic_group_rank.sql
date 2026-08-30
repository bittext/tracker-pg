-- Manual ordering for write-up topic groups (previously alphabetical only).
-- Denormalized onto each row like topic_group / topic_group_sort so no join is needed to render.

ALTER TABLE management_writeups
    ADD COLUMN IF NOT EXISTS topic_group_rank INT NOT NULL DEFAULT 0;

WITH ranked AS (
    SELECT
        owner_user_id,
        year,
        topic_group,
        dense_rank() OVER (
            PARTITION BY owner_user_id, year
            ORDER BY lower(trim(topic_group))
        ) - 1 AS rnk
    FROM (SELECT DISTINCT owner_user_id, year, topic_group FROM management_writeups
          WHERE topic_group IS NOT NULL AND trim(topic_group) <> '') s
)
UPDATE management_writeups w
SET topic_group_rank = r.rnk
FROM ranked r
WHERE w.owner_user_id = r.owner_user_id
  AND w.year = r.year
  AND w.topic_group = r.topic_group;
