-- Robinhood transaction cleanup (robinhood_transactions).
-- Matches dedupe semantics in RobinhoodCsvImportService / V8 migration.
--
-- Do not run with bash — use psql or:
--   bash scripts/cleanup-robinhood-transactions.sh --help
--
-- psql variables (set by the shell wrapper):
--   action          preview | dedupe | malformed | delete_year | truncate
--   year            calendar year for scoped deletes/dedupe (e.g. 2026)
--   owner_username  optional auth_users.username; empty = all users

\set ON_ERROR_STOP on

BEGIN;

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

DO $$
BEGIN
    IF COALESCE(trim(:'owner_username'), '') <> ''
        AND NOT EXISTS (
            SELECT 1
            FROM auth_users u
            WHERE lower(u.username) = lower(trim(:'owner_username'))
        ) THEN
        RAISE EXCEPTION 'owner_username not found: %', trim(:'owner_username');
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Preview (always printed for every action)
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
-- dedupe: keep one row per import dedupe key (same as V8 / CSV import)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    n INTEGER;
BEGIN
    IF :'action' <> 'dedupe' THEN
        RETURN;
    END IF;

    DELETE FROM robinhood_transactions t
    WHERE t.ctid IN (
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
    );

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'dedupe: deleted % duplicate row(s) for year %', n, :year;
END $$;

-- ---------------------------------------------------------------------------
-- malformed: rows from bad CSV column shift (trans_code = quantity digit, etc.)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    n INTEGER;
BEGIN
    IF :'action' <> 'malformed' THEN
        RETURN;
    END IF;

    DELETE FROM robinhood_transactions t
    WHERE t.ctid IN (
        SELECT s.row_ctid
        FROM rh_scope s
        WHERE trim(COALESCE(s.trans_code, '')) ~ '^[0-9]+\.?[0-9]*$'
    );

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'malformed: deleted % row(s) with numeric trans_code for year %', n, :year;
END $$;

-- ---------------------------------------------------------------------------
-- delete_year: remove all scoped rows (typical before a clean re-import)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    n INTEGER;
BEGIN
    IF :'action' <> 'delete_year' THEN
        RETURN;
    END IF;

    DELETE FROM robinhood_transactions t
    WHERE t.ctid IN (SELECT s.row_ctid FROM rh_scope s);

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'delete_year: deleted % row(s) for year %', n, :year;
END $$;

-- ---------------------------------------------------------------------------
-- truncate: all robinhood_transactions (ignores year; optional owner only)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    n INTEGER;
BEGIN
    IF :'action' <> 'truncate' THEN
        RETURN;
    END IF;

    IF COALESCE(trim(:'owner_username'), '') = '' THEN
        TRUNCATE robinhood_transactions;
        RAISE NOTICE 'truncate: truncated robinhood_transactions (all users)';
    ELSE
        DELETE FROM robinhood_transactions t
        WHERE t.owner_user_id = (
            SELECT u.id
            FROM auth_users u
            WHERE lower(u.username) = lower(trim(:'owner_username'))
        );
        GET DIAGNOSTICS n = ROW_COUNT;
        RAISE NOTICE 'truncate: deleted % row(s) for user %', n, trim(:'owner_username');
    END IF;
END $$;

COMMIT;

\echo '--- Done ---'
