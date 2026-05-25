-- Robinhood transaction cleanup (robinhood_transactions).
-- Matches dedupe semantics in RobinhoodCsvImportService / V8 migration.
--
-- psql variables (set by cleanup-robinhood-transactions.sh):
--   action          preview | dedupe | malformed | delete_year | truncate
--   year            calendar year (e.g. 2026)
--   owner_username  optional auth_users.username; empty = all users
--
-- Note: psql substitutes :variables only outside dollar-quoted bodies (stdin-safe).

\set ON_ERROR_STOP on

BEGIN;

-- Fail fast when --user names a missing login (plain SQL so :'owner_username' is substituted).
SELECT 1 / CASE
    WHEN COALESCE(trim(:'owner_username'), '') = '' THEN 1
    WHEN EXISTS (
        SELECT 1
        FROM auth_users u
        WHERE lower(u.username) = lower(trim(:'owner_username'))
    ) THEN 1
    ELSE 0
END AS owner_username_ok;

-- ---------------------------------------------------------------------------
-- Scope: calendar year + optional owner
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE rh_scope AS
SELECT
    t.ctid AS row_ctid,
    t.*
FROM robinhood_transactions t
WHERE
    t.activity_date IS NOT NULL
    AND t.activity_date >= make_timestamp(:year, 1, 1, 0, 0, 0)
    AND t.activity_date < make_timestamp(:year + 1, 1, 1, 0, 0, 0)
    AND (
        COALESCE(trim(:'owner_username'), '') = ''
        OR t.owner_user_id = (
            SELECT u.id
            FROM auth_users u
            WHERE lower(u.username) = lower(trim(:'owner_username'))
        )
    );

-- ---------------------------------------------------------------------------
-- Preview (always printed)
-- ---------------------------------------------------------------------------
\echo '--- Robinhood cleanup preview ---'
\echo 'action:' :action
\echo 'year:' :year
\echo 'owner_username:' :'owner_username'

SELECT count(*) AS scoped_rows FROM rh_scope;

SELECT count(*) AS exact_duplicate_rows_to_remove
FROM (
    SELECT s.row_ctid,
        row_number() OVER (
            PARTITION BY
                s.owner_user_id,
                s.activity_date,
                s.process_date,
                s.settle_date,
                NULLIF(trim(s.instrument), ''),
                NULLIF(trim(s.description), ''),
                NULLIF(trim(s.trans_code), ''),
                s.quantity,
                s.price,
                s.amount
            ORDER BY
                s.process_date NULLS LAST,
                s.settle_date NULLS LAST,
                s.activity_date NULLS LAST,
                s.row_ctid
        ) AS rn
    FROM rh_scope s
) d
WHERE d.rn > 1;

SELECT count(*) AS malformed_trans_code_rows
FROM rh_scope s
WHERE trim(COALESCE(s.trans_code, '')) ~ '^[0-9]+\.?[0-9]*$';

SELECT count(*) AS option_cash_mismatch_rows
FROM rh_scope s
WHERE
    s.description ~* '\m(call|put)\M'
    AND s.quantity IS NOT NULL
    AND s.price IS NOT NULL
    AND s.amount IS NOT NULL
    AND s.quantity <> 0
    AND s.price <> 0
    AND (
        abs(s.amount) / abs(s.quantity * s.price * 100) < 0.85
        OR abs(s.amount) / abs(s.quantity * s.price * 100) > 1.15
    );

\echo '--- Sample malformed / mismatch (up to 10) ---'
SELECT
    s.activity_date::date AS activity,
    s.trans_code,
    s.quantity,
    s.price,
    s.amount,
    left(s.description, 48) AS description
FROM rh_scope s
WHERE
    trim(COALESCE(s.trans_code, '')) ~ '^[0-9]+\.?[0-9]*$'
    OR (
        s.description ~* '\m(call|put)\M'
        AND s.quantity IS NOT NULL
        AND s.price IS NOT NULL
        AND s.amount IS NOT NULL
        AND s.quantity <> 0
        AND s.price <> 0
        AND (
            abs(s.amount) / abs(s.quantity * s.price * 100) < 0.85
            OR abs(s.amount) / abs(s.quantity * s.price * 100) > 1.15
        )
    )
ORDER BY s.activity_date, s.trans_code
LIMIT 10;

-- ---------------------------------------------------------------------------
-- dedupe (no-op unless action=dedupe; psql substitutes :'action' before send)
-- ---------------------------------------------------------------------------
WITH deleted AS (
    DELETE FROM robinhood_transactions t
    WHERE (:'action' = 'dedupe')
        AND t.ctid IN (
            SELECT d.row_ctid
            FROM (
                SELECT
                    s.row_ctid,
                    row_number() OVER (
                        PARTITION BY
                            s.owner_user_id,
                            s.activity_date,
                            s.process_date,
                            s.settle_date,
                            NULLIF(trim(s.instrument), ''),
                            NULLIF(trim(s.description), ''),
                            NULLIF(trim(s.trans_code), ''),
                            s.quantity,
                            s.price,
                            s.amount
                        ORDER BY
                            s.process_date NULLS LAST,
                            s.settle_date NULLS LAST,
                            s.activity_date NULLS LAST,
                            s.row_ctid
                    ) AS rn
                FROM rh_scope s
            ) d
            WHERE d.rn > 1
        )
    RETURNING 1
)
SELECT count(*) AS dedupe_rows_removed FROM deleted;

-- ---------------------------------------------------------------------------
-- malformed (numeric trans_code from CSV column shift)
-- ---------------------------------------------------------------------------
WITH deleted AS (
    DELETE FROM robinhood_transactions t
    WHERE (:'action' = 'malformed')
        AND t.ctid IN (
            SELECT s.row_ctid
            FROM rh_scope s
            WHERE trim(COALESCE(s.trans_code, '')) ~ '^[0-9]+\.?[0-9]*$'
        )
    RETURNING 1
)
SELECT count(*) AS malformed_rows_removed FROM deleted;

-- ---------------------------------------------------------------------------
-- delete_year (scoped rows only)
-- ---------------------------------------------------------------------------
WITH deleted AS (
    DELETE FROM robinhood_transactions t
    WHERE (:'action' = 'delete_year')
        AND t.ctid IN (SELECT s.row_ctid FROM rh_scope s)
    RETURNING 1
)
SELECT count(*) AS delete_year_rows_removed FROM deleted;

-- ---------------------------------------------------------------------------
-- truncate (all rows, or --user only; ignores year scope)
-- ---------------------------------------------------------------------------
WITH deleted AS (
    DELETE FROM robinhood_transactions t
    WHERE (:'action' = 'truncate')
        AND (
            COALESCE(trim(:'owner_username'), '') = ''
            OR t.owner_user_id = (
                SELECT u.id
                FROM auth_users u
                WHERE lower(u.username) = lower(trim(:'owner_username'))
            )
        )
    RETURNING 1
)
SELECT count(*) AS truncate_rows_removed FROM deleted;

COMMIT;

\echo '--- Done ---'
