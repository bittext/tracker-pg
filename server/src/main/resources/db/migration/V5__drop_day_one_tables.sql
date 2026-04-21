-- Remove Day One (journal) feature: drop in FK-safe order (children before parents).

DROP TABLE IF EXISTS management_day_one_log_tags;
DROP TABLE IF EXISTS management_day_one_attachments;
DROP TABLE IF EXISTS management_day_one_logs;
DROP TABLE IF EXISTS management_day_one_tag_defs;
