-- Life vs Work desks. Existing rows stay on LIFE except Management Calendar
-- entries whose type is WORK — those move to the Work desk.

ALTER TABLE management_documents
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
CREATE INDEX idx_management_documents_owner_desk
    ON management_documents (owner_user_id, desk);

ALTER TABLE management_recording_cache
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
ALTER TABLE management_recording_cache
    DROP CONSTRAINT uq_management_recording_cache_owner_path;
ALTER TABLE management_recording_cache
    ADD CONSTRAINT uq_management_recording_cache_owner_desk_path
        UNIQUE (owner_user_id, desk, relative_path);
CREATE INDEX idx_management_recording_cache_owner_desk_day
    ON management_recording_cache (owner_user_id, desk, recorded_day DESC);

ALTER TABLE management_accounts
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
CREATE INDEX idx_management_accounts_owner_desk
    ON management_accounts (owner_user_id, desk);

ALTER TABLE management_month_notes
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
CREATE INDEX idx_management_month_notes_owner_desk_year
    ON management_month_notes (owner_user_id, desk, year);

ALTER TABLE management_writeups
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
CREATE INDEX idx_management_writeups_owner_desk_year
    ON management_writeups (owner_user_id, desk, year);

ALTER TABLE management_due_items
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
CREATE INDEX idx_management_due_items_owner_desk
    ON management_due_items (owner_user_id, desk, active);

ALTER TABLE report_calendar_entries
    ADD COLUMN desk VARCHAR(16) NOT NULL DEFAULT 'LIFE';
CREATE INDEX idx_report_calendar_entries_owner_desk_date
    ON report_calendar_entries (owner_user_id, desk, entry_date);

UPDATE report_calendar_entries
SET desk = 'WORK'
WHERE upper(calendar_type) = 'WORK';

UPDATE management_writeups
SET desk = 'WORK'
WHERE lower(regexp_replace(trim(both from coalesce(topic_group, '')), '\s+', ' ', 'g'))
    IN ('office', 'learning');
