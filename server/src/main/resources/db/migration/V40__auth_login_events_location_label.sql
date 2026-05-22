-- Human-readable sign-in location (browser/timezone label from client, or trusted-location name).

ALTER TABLE auth_login_events
    ADD COLUMN location_label VARCHAR(180);

CREATE INDEX idx_auth_login_events_location_label ON auth_login_events (location_label)
    WHERE location_label IS NOT NULL;
