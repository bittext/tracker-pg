-- A deleted due stays off that month only. Recurring items remain on the other months.

ALTER TABLE management_due_occurrences
    ADD COLUMN skipped BOOLEAN NOT NULL DEFAULT FALSE;
