-- One-time cleanup: remove exact duplicate rows in robinhood_transactions for activity dates in calendar year 2026.
-- Definition matches com.svp.tracker.finance.service.RobinhoodCsvImportService row deduplication: same owner_user_id
-- and the nine trade columns (null-safe; trimmed strings, matching quantity/price/amount when present).
-- For each duplicate group, keeps a single row (lowest process_date, then settle_date, then stable ctid) and deletes the rest.

DELETE FROM robinhood_transactions t
WHERE t.ctid IN (
    SELECT d.ctid
    FROM (
        SELECT ctid,
            ROW_NUMBER() OVER (
                PARTITION BY
                    owner_user_id,
                    activity_date,
                    process_date,
                    settle_date,
                    NULLIF (TRIM(instrument), ''),
                    NULLIF (TRIM(description), ''),
                    NULLIF (TRIM(trans_code), ''),
                    quantity,
                    price,
                    amount
                ORDER BY
                    process_date NULLS LAST,
                    settle_date NULLS LAST,
                    activity_date NULLS LAST,
                    ctid
            ) AS rn
        FROM robinhood_transactions
        WHERE
            activity_date IS NOT NULL
            AND activity_date >= TIMESTAMP '2026-01-01 00:00:00'
            AND activity_date < TIMESTAMP '2027-01-01 00:00:00'
    ) d
    WHERE d.rn > 1
);
