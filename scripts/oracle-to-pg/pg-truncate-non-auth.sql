-- Run against the tracker-pg PostgreSQL database before loading Oracle data.
-- Keeps auth_users (and its id sequence) intact; clears app data and auth rows tied to sessions/MFA.

BEGIN;

TRUNCATE TABLE auth_mfa_challenges;
TRUNCATE TABLE auth_trusted_locations;

TRUNCATE TABLE management_tasks RESTART IDENTITY;
TRUNCATE TABLE fitness_exercise_day_logs RESTART IDENTITY;

TRUNCATE TABLE management_task_types RESTART IDENTITY;
TRUNCATE TABLE management_task_categories RESTART IDENTITY;

TRUNCATE TABLE fitness_exercises RESTART IDENTITY;
TRUNCATE TABLE fitness_body_weight RESTART IDENTITY;

TRUNCATE TABLE robinhood_transactions;

COMMIT;
