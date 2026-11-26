-- The other side of a cash input/output: where the money came from (input) or where it went (output),
-- e.g. "Robinhood Coastal Banking ••••6310". Optional; existing rows stay blank.

ALTER TABLE robinhood_account_cash_io
    ADD COLUMN counterparty VARCHAR(120);

CREATE INDEX idx_rh_cash_io_owner_counterparty
    ON robinhood_account_cash_io (owner_user_id, counterparty);
