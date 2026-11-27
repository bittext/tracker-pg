-- Marks ledger entries that came from a Robinhood history import, so a later import can remove one if
-- Robinhood reports that the transfer failed, was rejected or expired. Hand-entered rows stay false and are
-- never touched by an import.

ALTER TABLE robinhood_account_cash_io
    ADD COLUMN imported_from_robinhood BOOLEAN NOT NULL DEFAULT FALSE;
