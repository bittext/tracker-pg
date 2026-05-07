-- Remove Plaid-generated banking imports + their transactions, and clear all Plaid links.
-- Keeps manual banking imports and banking institutions.
--
-- Match Plaid-import files using any of:
--   - stored_relative_path starts with 'plaid/'
--   - original_filename starts with 'plaid_'
--   - parse_note starts with 'Plaid:'

BEGIN;

WITH plaid_files AS (
    SELECT f.id
    FROM banking_import_files f
    WHERE f.stored_relative_path ILIKE 'plaid/%'
       OR f.original_filename ~* '^plaid_'
       OR f.parse_note ILIKE 'Plaid:%'
)
DELETE FROM banking_transactions t
WHERE t.import_file_id IN (SELECT id FROM plaid_files);

WITH plaid_files AS (
    SELECT f.id
    FROM banking_import_files f
    WHERE f.stored_relative_path ILIKE 'plaid/%'
       OR f.original_filename ~* '^plaid_'
       OR f.parse_note ILIKE 'Plaid:%'
)
DELETE FROM banking_import_files f
WHERE f.id IN (SELECT id FROM plaid_files);

DELETE FROM banking_plaid_items;

COMMIT;
