ALTER TABLE finance_stock_alerts
    ADD COLUMN company_name VARCHAR(256),
    ADD COLUMN trigger_armed BOOLEAN NOT NULL DEFAULT TRUE;

-- Already-fired repeating alerts stay disarmed until price/session drops below threshold again.
UPDATE finance_stock_alerts
SET trigger_armed = FALSE
WHERE last_triggered_at IS NOT NULL
  AND repeat_mode = 'REPEAT';
