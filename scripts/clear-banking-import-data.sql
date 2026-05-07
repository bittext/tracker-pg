-- Reset all rows parsed from imports / Plaid OFX (all users).
-- Keeps banking_institutions and banking_plaid_items.
--
-- Do not run this file with bash — SQL is not shell syntax.
--
-- Preferred wrapper (handles DATABASE_URL, .env.stack, or Docker postgres):
--   bash scripts/clear-banking-import-data.sh
--
-- Or invoke psql yourself:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/clear-banking-import-data.sql
--
-- Files under tracker.finance.banking.import-directory are NOT removed; delete those
-- separately if you need disk space.

BEGIN;

-- CASCADE truncates banking_transactions first (FK targets banking_import_files). Multi-table
-- TRUNCATE without CASCADE still errors on some Postgres builds when import_files is touched second.
TRUNCATE banking_import_files CASCADE RESTART IDENTITY;

COMMIT;
