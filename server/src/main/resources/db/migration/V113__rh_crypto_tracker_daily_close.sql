-- Crypto Tracker follows Daily Tracker: one official SCHEDULED close per account per
-- Central calendar day, plus INTRADAY/MANUAL point-in-time rows.

ALTER TABLE robinhood_rh_crypto_snapshot
    ADD COLUMN IF NOT EXISTS account_suffix VARCHAR(8) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS account_number TEXT,
    ADD COLUMN IF NOT EXISTS label TEXT;

CREATE INDEX IF NOT EXISTS idx_rh_crypto_snapshot_owner_suffix_date
    ON robinhood_rh_crypto_snapshot (owner_user_id, account_suffix, snapshot_date DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rh_crypto_snapshot_scheduled_day
    ON robinhood_rh_crypto_snapshot (owner_user_id, snapshot_date, account_suffix)
    WHERE capture_kind = 'SCHEDULED' AND account_suffix <> '';

-- Historical 4-hour pulls were all marked SCHEDULED with no account suffix (Ammu book only).
-- Keep the latest row per owner/day as the official close; demote earlier rows to INTRADAY.
UPDATE robinhood_rh_crypto_snapshot s
SET capture_kind = 'INTRADAY'
WHERE s.capture_kind = 'SCHEDULED'
  AND COALESCE(s.account_suffix, '') = ''
  AND EXISTS (
      SELECT 1
      FROM robinhood_rh_crypto_snapshot newer
      WHERE newer.owner_user_id = s.owner_user_id
        AND newer.snapshot_date = s.snapshot_date
        AND newer.capture_kind = 'SCHEDULED'
        AND COALESCE(newer.account_suffix, '') = ''
        AND (newer.snapshot_at > s.snapshot_at
             OR (newer.snapshot_at = s.snapshot_at AND newer.id > s.id))
  );

UPDATE robinhood_rh_crypto_snapshot
SET account_suffix = '8696',
    label = COALESCE(NULLIF(BTRIM(label), ''), 'Ammu''s a/c (...8696)')
WHERE COALESCE(account_suffix, '') = '';
