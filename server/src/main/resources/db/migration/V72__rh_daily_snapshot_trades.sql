-- Store executed trades captured with each daily snapshot (alongside holdings and cash flows).

ALTER TABLE robinhood_rh_daily_snapshot
    ADD COLUMN IF NOT EXISTS trades_json TEXT NOT NULL DEFAULT '[]';
