-- A roadmap "chapter" is a journey that starts on a given day from a snapshot of every account.
-- Journeys without a start date keep their old behaviour (history from the first Daily Tracker close).

ALTER TABLE markets_journeys
    ADD COLUMN start_date   DATE,
    ADD COLUMN start_amount NUMERIC(18, 2);
