-- Backfill admin_cron_job rows created before created_at was marked non-updatable in JPA.

UPDATE admin_cron_job
SET created_at = COALESCE(updated_at, NOW())
WHERE created_at IS NULL;
