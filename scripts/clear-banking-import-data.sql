-- Reset all rows parsed from imports / Plaid OFX (all users).
-- Keeps banking_institutions and banking_plaid_items.
--
-- Run manually against your Postgres database when you want an empty ledger:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/clear-banking-import-data.sql
--
-- Files under tracker.finance.banking.import-directory are NOT removed; delete those
-- separately if you need disk space.

BEGIN;

TRUNCATE banking_transactions RESTART IDENTITY;

TRUNCATE banking_import_files RESTART IDENTITY;

COMMIT;
