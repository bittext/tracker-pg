-- Keep calendar/Due imports off after the user removes them (re-import used to resurrect the row).
ALTER TABLE finance_tax_desk_payments
    ADD COLUMN ignored BOOLEAN NOT NULL DEFAULT FALSE;
