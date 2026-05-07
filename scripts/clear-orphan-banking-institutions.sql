-- Remove banking institution rows that have no imports and no Plaid links.
-- Useful after clearing Plaid imports/links when institution names still appear in UI dropdowns.

BEGIN;

DELETE FROM banking_institutions bi
WHERE NOT EXISTS (
        SELECT 1 FROM banking_import_files f WHERE f.institution_id = bi.id
    )
  AND NOT EXISTS (
        SELECT 1 FROM banking_plaid_items p WHERE p.institution_id = bi.id
    );

COMMIT;
