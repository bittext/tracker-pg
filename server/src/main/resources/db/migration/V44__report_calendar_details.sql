-- Optional extra notes on calendar entries (shown in add/edit only, not in list body).
ALTER TABLE report_calendar_entries ADD COLUMN IF NOT EXISTS details TEXT;
