-- Run on the Oracle tracker schema (SQL*Plus / SQLcl / SQL Developer).
-- Export results to CSV or use DBeaver / Oracle SQL Developer "Export" per table, then load into PostgreSQL.
-- Do NOT export AUTH_USERS (PostgreSQL keeps existing bcrypt users).
--
-- Recommended: also skip AUTH_TRUSTED_LOCATIONS and AUTH_MFA_CHALLENGES (pg-truncate-non-auth.sql clears them);
-- Oracle rows reference Oracle user ids that will not match your Postgres auth_users ids.

-- Management
-- SELECT * FROM MANAGEMENT_TASK_CATEGORIES;
-- SELECT * FROM MANAGEMENT_TASK_TYPES;
-- SELECT * FROM MANAGEMENT_TASKS;

-- Fitness
-- SELECT * FROM FITNESS_EXERCISES;
-- SELECT * FROM FITNESS_EXERCISE_DAY_LOGS;
-- SELECT * FROM FITNESS_BODY_WEIGHT;

-- Finance (column names may differ on Oracle; align to robinhood_transactions in V1__tracker_schema.sql)
-- SELECT * FROM ROBINHOOD_TRANSACTIONS;

-- Skipped on purpose:
--   AUTH_USERS
--   AUTH_TRUSTED_LOCATIONS
--   AUTH_MFA_CHALLENGES
