-- Remove the extra "Road to my second million" journey that was created from Next million.
-- Entries cascade via markets_journey_entries.journey_id.

DELETE FROM markets_journeys
WHERE lower(trim(title)) = lower('Road to my second million');
