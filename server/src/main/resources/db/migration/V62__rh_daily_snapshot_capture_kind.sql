-- Distinguish scheduled 9 PM snapshots from ad-hoc manual captures.

ALTER TABLE robinhood_rh_daily_snapshot
    ADD COLUMN capture_kind VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED';

ALTER TABLE robinhood_rh_daily_snapshot
    DROP CONSTRAINT uq_rh_daily_snapshot_owner_date_suffix;

CREATE UNIQUE INDEX uq_rh_daily_snapshot_owner_date_suffix_scheduled
    ON robinhood_rh_daily_snapshot (owner_user_id, snapshot_date, account_suffix)
    WHERE capture_kind = 'SCHEDULED';

CREATE INDEX idx_rh_daily_snapshot_owner_date_kind
    ON robinhood_rh_daily_snapshot (owner_user_id, snapshot_date DESC, capture_kind);
